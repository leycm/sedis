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

import de.leycm.neck.instance.Initializable;
import lombok.NonNull;
import org.jetbrains.annotations.Contract;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Core interface for Redis-based caching operations within the Template API.
 * Provides a singleton access pattern and defines standard cache operations
 * including retrieval, storage, deletion, and set-based operations.
 * <p>
 * This interface extends {@link Initializable}, requiring implementations
 * to follow a specific initialization lifecycle. Implementations must ensure
 * thread safety as the cache may be accessed concurrently from multiple threads.
 * </p>
 * <p>
 * All operations are designed to be non-blocking where possible and should
 * handle Redis connection management internally. Keys should follow a consistent
 * naming convention determined by the implementation.
 * </p>
 *
 * @author LeyCM
 * @see Initializable
 * @since 1.0.1
 */
public interface RedisCache extends Initializable {

    /**
     * Retrieves the singleton instance of {@code RedisCache} registered with
     * the {@link Initializable} service loader mechanism.
     * <p>
     * This method provides global access to the cache implementation. The instance
     * must be initialized via {@link Initializable#register(Initializable, Class)} before use to ensure proper
     * connection establishment and resource allocation.
     * </p>
     *
     * @return the non-null singleton instance of {@code RedisCache}
     * @throws NullPointerException if no implementation of {@code RedisCache}
     *                              has been registered via the service loader
     * @see Initializable#getInstance(Class)
     */
    @Contract(pure = true)
    static @NonNull RedisCache getInstance() {
        return Initializable.getInstance(RedisCache.class);
    }

    /**
     * Retrieves a value from the cache associated with the specified key,
     * deserializing it to the requested type.
     * <p>
     * If the key does not exist or the deserialization fails (e.g., type mismatch),
     * an empty {@link Optional} is returned. The method handles serialization
     * format internally, typically using JSON or a binary format.
     * </p>
     *
     * @param <T>  the expected type of the cached value
     * @param key  the non-null cache key identifying the value
     * @param type the non-null {@link Class} object representing {@code T}
     * @return an {@link Optional} containing the deserialized value if present
     *         and compatible, otherwise {@link Optional#empty()}
     * @throws NullPointerException if {@code key} or {@code type} is null
     */
    <T> @NonNull Optional<T> get(final @NonNull String key,
                                 final @NonNull Class<T> type);

    /**
     * Creates a {@link RedisEntry} wrapper for a specific cache key and type.
     * <p>
     * This convenience method provides a more object-oriented way to interact
     * with a cache entry, potentially offering a fluent API for subsequent
     * operations like updates or deletion through the {@code RedisEntry} class.
     * </p>
     *
     * @param <T>  the type associated with the cache entry
     * @param key  the non-null cache key
     * @param type the non-null {@link Class} object representing {@code T}
     * @return a new, non-null {@link RedisEntry} instance bound to this cache,
     *         the specified key, and type
     * @throws NullPointerException if {@code key} or {@code type} is null
     */
    default <T> @NonNull RedisEntry<T> getEntry(final @NonNull String key,
                                                final @NonNull Class<T> type) {
        return new RedisEntry<>(this, type, key);
    }

    /**
     * Creates a {@link RedisRepository} wrapper for managing collections of entities
     * of a specific type with custom key mapping logic.
     * <p>
     * This method provides a repository pattern abstraction over the Redis cache,
     * allowing for CRUD operations on entities of type {@code T} with keys of type {@code K}.
     * The {@code keyMapper} function determines how entity identifiers are transformed
     * into Redis cache keys, enabling flexible naming strategies.
     * </p>
     * <p>
     * The returned repository supports operations like saving, retrieving, and deleting
     * entities, as well as checking for existence, all while maintaining proper
     * serialization/deserialization for the entity type.
     * </p>
     *
     * @param <T>        the type of entities managed by the repository
     * @param <K>        the type of entity identifiers (keys)
     * @param tClass     the non-null {@link Class} object representing the entity type {@code T}
     * @param keyMapper  the non-null function that maps entity identifiers of type {@code K}
     *                   to Redis cache keys as {@link String}s
     * @return a new, non-null {@link RedisRepository} instance bound to this cache,
     *         the specified entity type, and key mapping function
     * @throws NullPointerException if {@code tClass} or {@code keyMapper} is null
     * @see RedisRepository
     */
    default <T, K> @NonNull RedisRepository<T, K> getRepo(final @NonNull Class<T> tClass,
                                                          final @NonNull Function<K, String> keyMapper,
                                                          final @NonNull Function<String, K> keyReMapper) {
        return new RedisRepository<>(this, tClass, keyMapper, keyReMapper);
    }

    /**
     * Stores a value in the cache under the specified key.
     * <p>
     * The value is serialized according to the implementation's strategy
     * (e.g., JSON). If the key already exists, its value is overwritten.
     * The operation is typically atomic. The expiration policy (TTL) is
     * determined by the implementation or configuration.
     * </p>
     *
     * @param key   the non-null cache key
     * @param value the non-null object to store
     * @throws NullPointerException if {@code key} or {@code value} is null
     */
    void set(final @NonNull String key, final @NonNull Object value);

    /**
     * Checks whether a cache entry exists for the specified key.
     * <p>
     * This method performs a lightweight existence check without deserializing
     * or loading the actual value. It is more efficient than {@link #get(String, Class)}
     * when only the presence of a key needs to be verified.
     * </p>
     * <p>
     * The check is performed by attempting to retrieve the value as a generic
     * {@link Object} and verifying if the result is present. Implementations may
     * optimize this operation using Redis-specific commands like {@code EXISTS}.
     * </p>
     *
     * @param key the non-null cache key to check for existence
     * @return {@code true} if the key exists in the cache and has an associated value,
     *         {@code false} otherwise
     * @throws NullPointerException if {@code key} is null
     */
    default boolean contains(final @NonNull String key) {
        return get(key, Object.class).isPresent();
    }

    /**
     * Removes the key-value pair associated with the specified key from the cache.
     * <p>
     * If the key does not exist, the operation has no effect. This is a
     * non-blocking operation.
     * </p>
     *
     * @param key the non-null key of the entry to delete
     * @throws NullPointerException if {@code key} is null
     */
    void delete(final @NonNull String key);

    /**
     * Removes all entries from the cache.
     * <p>
     * This operation typically uses the Redis {@code FLUSHDB} or {@code FLUSHALL}
     * command, affecting all keys in the current database or all databases,
     * depending on implementation. Use with extreme caution in production
     * environments.
     * </p>
     */
    void deleteAll();

    /**
     * Adds a member to a Redis Set identified by the given key.
     * <p>
     * If the key does not exist, a new set is created containing the member.
     * If the member already exists in the set, the operation is ignored.
     * This operation corresponds to the Redis {@code SADD} command.
     * </p>
     *
     * @param key   the non-null key identifying the set
     * @param value the non-null member to add to the set
     * @throws NullPointerException if {@code key} or {@code value} is null
     */
    void add(final @NonNull String key,
             final @NonNull String value);

    /**
     * Removes a member from a Redis Set identified by the given key.
     * <p>
     * If the member does not exist in the set, the operation is ignored.
     * If the set becomes empty after removal, the key is removed from the cache.
     * This operation corresponds to the Redis {@code SREM} command.
     * </p>
     *
     * @param key   the non-null key identifying the set
     * @param value the non-null member to remove from the set
     * @throws NullPointerException if {@code key} or {@code value} is null
     */
    void rem(final @NonNull String key,
             final @NonNull String value);

    /**
     * Retrieves all members of a Redis Set identified by the given key.
     * <p>
     * Returns an empty set if the key does not exist. The returned {@link Set}
     * is a snapshot of the set's members at the time of the call. This operation
     * corresponds to the Redis {@code SMEMBERS} command.
     * </p>
     *
     * @param key the non-null key identifying the set
     * @return a non-null, potentially empty {@link Set} containing all members
     * @throws NullPointerException if {@code key} is null
     */
    @NonNull Set<String> members(final @NonNull String key);

    /**
     * Clears any internal client-side cache maintained by the implementation.
     * <p>
     * This method does not affect data stored in the Redis server. Its purpose
     * is to invalidate any local, in-memory caches (e.g., read-through caches)
     * that the {@code RedisCache} implementation might use to improve performance,
     * forcing subsequent reads to fetch data directly from Redis.
     * </p>
     */
    void clearCache();

}