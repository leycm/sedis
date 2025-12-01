/**
 * LECP-LICENSE NOTICE
 * <br><br>
 * This Sourcecode is under the LECP-LICENSE. <br>
 * License at: <a href="https://github.com/leycm/leycm/blob/main/LICENSE">GITHUB</a>
 * <br><br>
 * Copyright (c) LeyCM <a href="mailto:leycm@proton.me">leycm@proton.me</a> l <br>
 * Copyright (c) maintainers <br>
 * Copyright (c) contributors
 */
package de.leycm.sedis;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * LocalRedisCache Implementation
 *
 * <p>
 * Thread-safe Redis-based cache implementation with local in-memory caching
 * and PubSub-based cache invalidation across distributed instances.
 * </p>
 *
 * <p>
 * This implementation provides a two-tier caching strategy:
 * <ul>
 *     <li>Local in-memory cache for fast access</li>
 *     <li>Redis backend for persistence and distribution</li>
 *     <li>PubSub mechanism for cache invalidation across instances</li>
 * </ul>
 * </p>
 *
 * @author LeyCM
 * @since 1.0.1
 */
@Slf4j
public class MultiServiceRedisCache implements LocalRedisCache {

    private static final String INVALIDATE_CHANNEL = "cache:invalidate";
    private static final String INVALIDATE_ALL_CHANNEL = "cache:invalidate:all";

    private final JedisPool jedisPool;
    private final Gson gson;
    private final ConcurrentHashMap<String, Object> localCache;
    private final ExecutorService pubSubExecutor;
    private volatile boolean isShutdown = false;

    /**
     * Constructs a new LocalRedisCacheImpl instance.
     *
     * @param redisHost the Redis server hostname
     * @param redisPort the Redis server port
     */
    public MultiServiceRedisCache(@NonNull final String redisHost, final int redisPort) {
        this(redisHost, redisPort, null);
    }

    /**
     * Constructs a new LocalRedisCacheImpl instance with optional password.
     *
     * @param redisHost     the Redis server hostname
     * @param redisPort     the Redis server port
     * @param redisPassword the Redis password (nullable)
     */
    public MultiServiceRedisCache(final @NonNull String redisHost,
                               final int redisPort,
                               final @Nullable String redisPassword) {
        final JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);

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
        log.info("LocalRedisCache initialized with Redis at {}:{}", redisHost, redisPort);
    }

    /**
     * Constructs a new LocalRedisCacheImpl instance with optional password.
     *
     * @param jedisPool the Redis password
     */
    public MultiServiceRedisCache(final @NonNull JedisPool jedisPool) {

        this.jedisPool = jedisPool;

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
        log.info("LocalRedisCache initialized with existing RedisPool");
    }

    @Override
    public <T> @NonNull Optional<T> get(@NonNull final String key,
                                        @NonNull final Class<T> type) {
        if (isShutdown) {
            log.warn("Cache is shutdown, returning empty for key: {}", key);
            return Optional.empty();
        }

        final Object cached = localCache.get(key);
        if (cached != null) {
            try {
                return Optional.of(type.cast(cached));
            } catch (ClassCastException e) {
                log.warn("Type mismatch in local cache for key: {}, removing entry", key);
                localCache.remove(key);
            }
        }

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

    @Override
    public void set(@NonNull final String key, @NonNull final Object value) {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring set operation for key: {}", key);
            return;
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            final String json = gson.toJson(value);
            jedis.set(key, json);

            localCache.put(key, value);

            jedis.publish(INVALIDATE_CHANNEL, key);

            log.debug("Set value for key: {}", key);
        } catch (Exception e) {
            log.error("Error writing to Redis for key: {}", key, e);
        }
    }

    @Override
    public void delete(@NonNull final String key) {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring delete operation for key: {}", key);
            return;
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.del(key);
            localCache.remove(key);

            jedis.publish(INVALIDATE_CHANNEL, key);

            log.debug("Deleted key: {}", key);
        } catch (Exception e) {
            log.error("Error deleting from Redis for key: {}", key, e);
        }
    }

    @Override
    public void deleteAll() {
        if (isShutdown) {
            log.warn("Cache is shutdown, ignoring deleteAll operation");
            return;
        }

        try (final Jedis jedis = jedisPool.getResource()) {
            jedis.flushDB();
            localCache.clear();

            jedis.publish(INVALIDATE_ALL_CHANNEL, "all");

            log.info("Deleted all cache entries");
        } catch (Exception e) {
            log.error("Error flushing Redis database", e);
        }
    }

    public void shutdown() {
        if (isShutdown) return;

        isShutdown = true;
        log.info("Shutting down LocalRedisCache...");

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

        log.info("LocalRedisCache shut down successfully");
    }

    /**
     * Invalidates a key in the local cache only.
     *
     * @param key the key to invalidate
     */
    private void invalidateLocal(@NonNull final String key) {
        localCache.remove(key);
        log.debug("Local cache invalidated for key: {}", key);
    }

    /**
     * Clears the entire local cache.
     */
    private void invalidateAllLocal() {
        localCache.clear();
        log.debug("All local cache entries invalidated");
    }

    /**
     * Starts the Redis PubSub listener for cache invalidation.
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