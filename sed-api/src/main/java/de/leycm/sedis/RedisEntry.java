/**
 * LECP-LICENSE NOTICE
 * <br><br>
 * This Sourcecode is under the LECP-LICENSE. <br>
 * License at: <a href="https://github.com/leycm/leycm/blob/main/LICENSE">GITHUB</a>
 * <br><br>
 * Copyright (c) LeyCM <a href="mailto:leycm@proton.me">leycm@proton.me</a><br>
 * Copyright (c) maintainers <br>
 * Copyright (c) contributors
 */
package de.leycm.sedis;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Type-safe wrapper for a single Redis cache entry with convenience methods.
 * <p>
 * This record provides a fluent API for common cache operations on a specific
 * key with a known type. It encapsulates the cache instance, type information,
 * and key to simplify cache access patterns.
 * </p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RedisEntry<User> userEntry = new RedisEntry<>(cache, User.class, "user:123");
 *
 * // Get user with type safety
 * Optional<User> user = userEntry.get();
 *
 * // Set user
 * userEntry.set(new User("John", "Doe"));
 *
 * // Get nullable value
 * User nullableUser = userEntry.getValue();
 * }</pre>
 *
 * @param <T> the type of the cached object
 * @param cache the underlying {@link RedisCache} instance
 * @param tClass the {@link Class} object representing type {@code T}
 * @param key the cache key for this entry
 * @author LeyCM
 * @version 1.1.2
 * @see RedisCache
 * @see Optional
 */
public record RedisEntry<T>(@NonNull RedisCache cache,
                            @NonNull Class<T> tClass,
                            @NonNull String key) {

    /**
     * Retrieves the cached value for this entry.
     * <p>
     * This is a convenience method that delegates to {@link RedisCache#get(String, Class)}
     * with the stored cache instance, type, and key.
     * </p>
     *
     * @return an {@link Optional} containing the cached value, or empty if not found
     * @see RedisCache#get(String, Class)
     */
    public @NonNull Optional<T> get() {
        return cache.get(key, tClass);
    }

    /**
     * Stores a value in the cache for this entry.
     * <p>
     * This is a convenience method that delegates to {@link RedisCache#set(String, Object)}
     * with the stored cache instance and key.
     * </p>
     *
     * @param value the value to cache (must not be {@code null})
     * @throws NullPointerException if {@code value} is {@code null}
     * @see RedisCache#set(String, Object)
     */
    public void set(final @NonNull T value) {
        cache.set(key, value);
    }

    /**
     * Retrieves the cached value or {@code null} if not found.
     * <p>
     * This method provides a nullable alternative to {@link #get()} for
     * convenience when null checks are preferred over {@link Optional} handling.
     * </p>
     *
     * @return the cached value, or {@code null} if not found
     * @see #get()
     */
    public @Nullable T getValue() {
        return cache.get(key, tClass).orElse(null);
    }

    /**
     * Deletes this entry from the cache.
     * <p>
     * This is a convenience method that delegates to {@link RedisCache#delete(String)}
     * with the stored cache instance and key.
     * </p>
     *
     * @see RedisCache#delete(String)
     */
    public void delete() {
        cache.delete(key);
    }
}