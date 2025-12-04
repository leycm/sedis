/**
 * LECP-LICENSE NOTICE
 * <br><br>
 * This Sourcecode is under the LECP-LICENSE. <br>
 * License at: <a href="https://github.com/leycm/leycm/blob/main/LICENSE">GITHUB</a>
 * <br><br>
 * Copyright (c) LeyCM <a href="mailto:leycm@proton.me">leycm@proton.me</a> l <br>
 * Copyright (c) maintainers <br>
 * Copyright (c) contributors
 *
 * @version 1.1.2
 */
package de.leycm.sedis.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import de.leycm.sedis.RedisCache;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of {@link RedisCache} providing distributed caching with local cache
 * optimization and Pub/Sub-based invalidation across multiple instances.
 * <p>
 * This class implements a two-level caching architecture:
 * </p>
 * <ol>
 *   <li><b>Local in-memory cache:</b> Provides ultra-fast read access for frequently accessed items</li>
 *   <li><b>Redis persistence layer:</b> Ensures data durability and cross-instance consistency</li>
 *   <li><b>Pub/Sub synchronization:</b> Maintains cache coherence across distributed deployments</li>
 * </ol>
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Thread-safe operations with proper synchronization</li>
 *   <li>Automatic type serialization/deserialization using Gson</li>
 *   <li>Graceful degradation when Redis is unavailable (local cache continues to work)</li>
 *   <li>Automatic reconnection for Pub/Sub listener</li>
 *   <li>Resource cleanup on shutdown</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RedisCache cache = new LocalRedisCache("localhost", 6379);
 * Optional<User> user = cache.get("user:123", User.class);
 * cache.set("user:123", new User("John", "Doe"));
 * }</pre>
 *
 * <h2>Concurrency</h2>
 * <p>
 * All public methods are thread-safe. The implementation uses:
 * <ul>
 *   <li>{@link ConcurrentHashMap} for local cache operations</li>
 *   <li>Jedis connection pooling for Redis operations</li>
 *   <li>Single-threaded executor for Pub/Sub handling</li>
 * </ul>
 * </p>
 *
 * @author LeyCM
 * @version 1.1.2
 * @since 1.0.1
 * @see RedisCache
 * @see JedisPool
 * @see ConcurrentHashMap
 */
@Slf4j
public class LocalRedisCache implements RedisCache {

    /**
     * Redis channel for targeted cache invalidation messages.
     * <p>
     * When a key is modified or deleted, this channel broadcasts the key name
     * to all listening instances to invalidate their local cache.
     * </p>
     */
    private static final String INVALIDATE_CHANNEL = "cache:invalidate";

    /**
     * Redis channel for global cache invalidation messages.
     * <p>
     * When {@link #deleteAll()} is called, this channel broadcasts a message
     * to all listening instances to clear their entire local cache.
     * </p>
     */
    private static final String INVALIDATE_ALL_CHANNEL = "cache:invalidate:all";

    /**
     * Connection pool for Redis operations.
     * <p>
     * Uses {@link JedisPool} to manage Redis connections efficiently with
     * connection pooling and automatic resource cleanup.
     * </p>
     */
    private final JedisPool jedisPool;

    /**
     * JSON serializer/deserializer for object storage.
     * <p>
     * Configured with {@link GsonBuilder#serializeNulls()} to preserve
     * null values during serialization.
     * </p>
     */
    private final Gson gson;

    /**
     * Local in-memory cache for fast access.
     * <p>
     * Uses {@link ConcurrentHashMap} to provide thread-safe operations
     * without external synchronization.
     * </p>
     */
    private final ConcurrentHashMap<String, Object> localCache;

    /**
     * Executor service for Pub/Sub listener thread.
     * <p>
     * Uses a single-threaded executor with daemon thread to prevent
     * blocking JVM shutdown.
     * </p>
     */
    private final ExecutorService pubSubExecutor;

    /**
     * Shutdown flag to prevent operations after shutdown.
     * <p>
     * Marked as {@code volatile} to ensure visibility across threads.
     * </p>
     */
    private volatile boolean isShutdown = false;

    /**
     * Constructs a new LocalRedisCache instance without authentication.
     * <p>
     * Equivalent to calling {@link #LocalRedisCache(String, int, String)} with
     * {@code null} password.
     * </p>
     *
     * @param redisHost the Redis server hostname (must not be {@code null})
     * @param redisPort the Redis server port
     * @throws NullPointerException if {@code redisHost} is {@code null}
     * @see #LocalRedisCache(String, int, String)
     */
    public LocalRedisCache(final @NonNull String redisHost, final int redisPort) {
        this(redisHost, redisPort, null);
    }

    /**
     * Constructs a new LocalRedisCache instance with authentication support.
     * <p>
     * Initializes:
     * <ol>
     *   <li>Jedis connection pool with optimized configuration</li>
     *   <li>Gson instance for JSON serialization</li>
     *   <li>Local concurrent cache</li>
     *   <li>Pub/Sub listener for cache invalidation</li>
     * </ol>
     * </p>
     *
     * @param redisHost     the Redis server hostname (must not be {@code null})
     * @param redisPort     the Redis server port
     * @param redisPassword the Redis password for authentication, or {@code null} for no auth
     * @throws NullPointerException if {@code redisHost} is {@code null}
     * @see JedisPoolConfig
     * @see #startPubSubListener()
     */
    public LocalRedisCache(final @NonNull String redisHost,
                           final int redisPort,
                           final @Nullable String redisPassword) {
        final JedisPoolConfig poolConfig = createPoolConfig();

        this.jedisPool = redisPassword != null
                ? new JedisPool(poolConfig, redisHost, redisPort, 2000, redisPassword)
                : new JedisPool(poolConfig, redisHost, redisPort);

        this.gson = new GsonBuilder()
                .serializeNulls()
                .create();

        this.localCache = new ConcurrentHashMap<>();
        this.pubSubExecutor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "redis-pubsub-listener");
            thread.setDaemon(true);
            return thread;
        });

        startPubSubListener();
        log.info("RedisCache initialized with Redis at {}:{}", redisHost, redisPort);
    }

    /**
     * Constructs a new LocalRedisCache instance using an existing {@link JedisPool}.
     * <p>
     * Useful when connection pooling configuration needs to be shared across
     * multiple components or when advanced pool configuration is required.
     * </p>
     *
     * @param jedisPool the existing Jedis connection pool (must not be {@code null})
     * @throws NullPointerException if {@code jedisPool} is {@code null}
     */
    public LocalRedisCache(final @NonNull JedisPool jedisPool, final Gson gson) {
        this.jedisPool = jedisPool;

        this.gson = gson;

        this.localCache = new ConcurrentHashMap<>();
        this.pubSubExecutor = Executors.newSingleThreadExecutor(r -> {
            final Thread thread = new Thread(r, "redis-pubsub-listener");
            thread.setDaemon(true);
            return thread;
        });

        startPubSubListener();
        log.info("RedisCache initialized with existing RedisPool");
    }

    /**
     * Creates and configures a {@link JedisPoolConfig} with optimized settings.
     * <p>
     * Configuration includes:
     * <ul>
     *   <li>Max total connections: 20</li>
     *   <li>Max idle connections: 10</li>
     *   <li>Min idle connections: 5</li>
     *   <li>Connection validation on borrow/return/idle</li>
     * </ul>
     * </p>
     *
     * @return configured {@link JedisPoolConfig} instance
     */
    private JedisPoolConfig createPoolConfig() {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        return poolConfig;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation details:
     * <ol>
     *   <li>Checks shutdown state - returns empty if shutdown</li>
     *   <li>Attempts to retrieve from local cache first</li>
     *   <li>On cache miss, queries Redis and updates local cache</li>
     *   <li>Handles type casting exceptions gracefully</li>
     *   <li>Returns {@link Optional#empty()} on any failure</li>
     * </ol>
     * </p>
     *
     * @param <T>  the type of the cached object
     * @param key  the cache key (must not be {@code null})
     * @param type the {@link Class} of the cached object (must not be {@code null})
     * @return an {@link Optional} containing the cached object, or empty if not found
     * @throws NullPointerException if {@code key} or {@code type} is {@code null}
     * @throws JsonSyntaxException if Redis contains invalid JSON for the given type
     */
    @Override
    public <T> @NonNull Optional<T> get(final @NonNull String key,
                                        final @NonNull Class<T> type) {
        if (isShutdown) {
            log.warn("Cache is shutdown, returning empty for key: {}", key);
            return Optional.empty();
        }

        // Try local cache first
        final Object cached = localCache.get(key);
        if (cached != null) {
            try {
                return Optional.of(type.cast(cached));
            } catch (ClassCastException e) {
                log.warn("Type mismatch in local cache for key: {}, removing entry", key);
                localCache.remove(key);
                // Fall through to Redis query
            }
        }

        // Query Redis on local cache miss
        try (final Jedis jedis = jedisPool.getResource()) {
            final String json = jedis.get(key);
            if (json != null) {
                try {
                    final T value = gson.fromJson(json, type);
                    localCache.put(key, value);
                    return Optional.of(value);
                } catch (JsonSyntaxException e) {
                    log.error("Failed to deserialize value for key: {}", key, e);
                    return Optional.empty();
                }
            }
        } catch (Exception e) {
            log.error("Error reading from Redis for key: {}", key, e);
        }

        return Optional.empty();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation details:
     * <ol>
     *   <li>Serializes object to JSON using Gson</li>
     *   <li>Stores in Redis with default persistence</li>
     *   <li>Updates local cache</li>
     *   <li>Publishes invalidation message to synchronize other instances</li>
     * </ol>
     * </p>
     *
     * @param key   the cache key (must not be {@code null})
     * @param value the object to cache (must not be {@code null})
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    @Override
    public void set(final @NonNull String key, final @NonNull Object value) {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring set operation for key: {}", key);
            return;
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            final String json = gson.toJson(value);
            jedis.set(key, json);

            localCache.put(key, value);

            // Notify other instances to invalidate their local cache
            jedis.publish(INVALIDATE_CHANNEL, key);

            log.debug("Set value for key: {}", key);
        } catch (Exception e) {
            log.error("Error writing to Redis for key: {}", key, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Implementation details:
     * <ol>
     *   <li>Deletes key from Redis</li>
     *   <li>Removes from local cache</li>
     *   <li>Publishes invalidation message to synchronize other instances</li>
     * </ol>
     * </p>
     *
     * @param key the cache key to delete (must not be {@code null})
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @Override
    public void delete(final @NonNull String key) {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring delete operation for key: {}", key);
            return;
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
            localCache.remove(key);

            // Notify other instances to invalidate their local cache
            jedis.publish(INVALIDATE_CHANNEL, key);

            log.debug("Deleted key: {}", key);
        } catch (Exception e) {
            log.error("Error deleting from Redis for key: {}", key, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * <b>Warning:</b> This operation clears the entire Redis database (FLUSHDB).
     * Use with caution in production environments.
     * </p>
     * Implementation details:
     * <ol>
     *   <li>Flushes Redis database</li>
     *   <li>Clears local cache</li>
     *   <li>Publishes global invalidation message to synchronize other instances</li>
     * </ol>
     */
    @Override
    public void deleteAll() {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring deleteAll operation");
            return;
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
            localCache.clear();

            // Notify all instances to clear their local caches
            jedis.publish(INVALIDATE_ALL_CHANNEL, "all");

            log.info("Deleted all cache entries");
        } catch (Exception e) {
            log.error("Error flushing Redis database", e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Adds a member to a Redis set. Sets maintain unique members.
     * </p>
     *
     * @param key   the set key (must not be {@code null})
     * @param value the member to add (must not be {@code null})
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    @Override
    public void add(final @NonNull String key,
                     final @NonNull String value) {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring sAdd for key: {}", key);
            return;
        }
        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(key, value);
            log.debug("Added '{}' to set '{}'", value, key);
        } catch (Exception e) {
            log.error("Error adding '{}' to set '{}'", value, key, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Removes a member from a Redis set.
     * </p>
     *
     * @param key   the set key (must not be {@code null})
     * @param value the member to remove (must not be {@code null})
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    @Override
    public void rem(final @NonNull String key,
                     final @NonNull String value) {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring sRem for key: {}", key);
            return;
        }
        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.srem(key, value);
            log.debug("Removed '{}' from set '{}'", value, key);
        } catch (Exception e) {
            log.error("Error removing '{}' from set '{}'", value, key, e);
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * Retrieves all members of a Redis set.
     * </p>
     *
     * @param key the set key (must not be {@code null})
     * @return a {@link Set} containing all members of the set, or empty set if key doesn't exist
     * @throws NullPointerException if {@code key} is {@code null}
     */
    @Override
    public @NonNull Set<String> members(final @NonNull String key) {
        if (isShutdown) {
            log.warn("Cache is shutdown, returning empty set for '{}'", key);
            return Collections.emptySet();
        }
        try (final Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(key);
        } catch (Exception e) {
            log.error("Error fetching members of set '{}'", key, e);
            return Collections.emptySet();
        }
    }

    @Override
    public void clearCache() {
        localCache.clear();
    }

    /**
     * Gracefully shuts down the cache instance.
     * <p>
     * Performs the following cleanup:
     * <ol>
     *   <li>Sets shutdown flag to prevent new operations</li>
     *   <li>Stops Pub/Sub listener with 5-second timeout</li>
     *   <li>Closes Redis connection pool</li>
     *   <li>Clears local cache</li>
     * </ol>
     * This method is idempotent - calling it multiple times has no additional effect.
     * </p>
     */
    public void shutdown() {
        if (isShutdown) return;

        isShutdown = true;
        log.info("Shutting down RedisCache...");

        // Shutdown Pub/Sub listener
        pubSubExecutor.shutdown();
        try {
            if (!pubSubExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pubSubExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            pubSubExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        jedisPool.close();

        localCache.clear();

        log.info("RedisCache shut down successfully");
    }

    /**
     * Invalidates a specific key in the local cache only.
     * <p>
     * This method is called when receiving invalidation messages from other
     * cache instances via Pub/Sub.
     * </p>
     *
     * @param key the key to invalidate in the local cache (must not be {@code null})
     * @throws NullPointerException if {@code key} is {@code null}
     */
    private void invalidateLocal(final @NonNull String key) {
        localCache.remove(key);
        log.debug("Local cache invalidated for key: {}", key);
    }

    /**
     * Clears the entire local cache.
     * <p>
     * This method is called when receiving global invalidation messages
     * from other cache instances via Pub/Sub.
     * </p>
     */
    private void invalidateAllLocal() {
        localCache.clear();
        log.debug("All local cache entries invalidated");
    }

    /**
     * Starts the Redis Pub/Sub listener thread for cache invalidation.
     * <p>
     * The listener subscribes to two channels:
     * <ol>
     *   <li>{@link #INVALIDATE_CHANNEL} - for specific key invalidation</li>
     *   <li>{@link #INVALIDATE_ALL_CHANNEL} - for global cache invalidation</li>
     * </ol>
     * The listener automatically reconnects on failure with a 5-second delay.
     * </p>
     */
    private void startPubSubListener() {
        pubSubExecutor.submit(() -> {
            while (!isShutdown) {
                try (final Jedis jedis = jedisPool.getResource()) {
                    log.info("Starting Redis PubSub listener...");
                    jedis.subscribe(new JedisPubSub() {
                        @Override
                        public void onMessage(String channel, String message) {
                            if (INVALIDATE_CHANNEL.equals(channel)) {
                                invalidateLocal(message);
                            } else if (INVALIDATE_ALL_CHANNEL.equals(channel)) {
                                invalidateAllLocal();
                            }
                        }

                        @Override
                        public void onSubscribe(String channel, int subscribedChannels) {
                            log.info("Subscribed to Redis channel: {}", channel);
                        }
                    }, INVALIDATE_CHANNEL, INVALIDATE_ALL_CHANNEL);
                } catch (Exception e) {
                    if (!isShutdown) {
                        log.error("PubSub listener error, reconnecting in 5 seconds...", e);
                        try {
                            //noinspection BusyWait
                            Thread.sleep(5000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            log.info("PubSub listener stopped");
        });
    }

}