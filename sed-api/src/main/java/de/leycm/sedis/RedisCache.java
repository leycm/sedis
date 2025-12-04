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

/**
 * RedisCache
 *
 * <p>
 * Core interface for the Template API. Provides a singleton instance
 * via {@link #getInstance()} and defines a standard initialization contract
 * through {@link Initializable}.
 * </p>
 *
 * <p>Implementations should be thread-safe and initialized before use.</p>
 *
 * @author LeyCM
 * @since 1.0.1
 */
public interface RedisCache extends Initializable {

    /**
     * Returns the singleton instance of the {@code RedisCache}.
     *
     * <p>
     * This method relies on the {@link Initializable#getInstance(Class)} mechanism to retrieve
     * the registered implementation. If no implementation has been registered, a
     * {@link NullPointerException} is thrown.
     * </p>
     *
     * @return the singleton instance of {@code RedisCache}
     * @throws NullPointerException if no implementation is registered
     * @see Initializable#getInstance(Class)
     */
    @Contract(pure = true)
    static @NonNull RedisCache getInstance() {
        return Initializable.getInstance(RedisCache.class);
    }

    <T> @NonNull Optional<T> get(final @NonNull String key,
                                 final @NonNull Class<T> type);

    void set(final @NonNull String key, final @NonNull Object value);

    default <T> @NonNull RedisEntry<T> getEntry(final @NonNull String key,
                                                final @NonNull Class<T> type) {
        return new RedisEntry<>(this, type, key);
    }

    void delete(final @NonNull String key);

    void deleteAll();

    void add(final @NonNull String key,
             final @NonNull String value);

    void rem(final @NonNull String key,
             final @NonNull String value);

    @NonNull Set<String> members(final @NonNull String key);

    void clearCache();

}