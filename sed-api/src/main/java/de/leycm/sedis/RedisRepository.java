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

import lombok.Data;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Repository pattern implementation for Redis cache that manages collections of typed objects.
 * <p>
 * This record provides a higher-level abstraction over {@link RedisCache} for managing
 * collections of objects of type {@code T} with keys of type {@code K}. It automatically
 * maintains a Redis set to track all keys in the repository, enabling iteration and
 * key enumeration.
 * </p>
 *
 * <h2>Key Features</h2>
 * <ul>
 *   <li>Automatic key tracking using Redis sets</li>
 *   <li>Type-safe operations with compile-time type checking</li>
 *   <li>{@link Iterable} implementation for easy iteration</li>
 *   <li>Automatic key generation using configurable key mapper</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * RedisRepository<User, String> userRepo = new RedisRepository<>(
 *     cache,
 *     User.class,
 *     username -> username  // Simple key mapper
 * );
 *
 * // Store a user
 * userRepo.set("john_doe", new User("John", "Doe"));
 *
 * // Retrieve a user
 * Optional<User> user = userRepo.get("john_doe");
 *
 * // Iterate all users
 * for (User u : userRepo) {
 *     System.out.println(u.getName());
 * }
 * }</pre>
 *
 * <h2>Key Generation</h2>
 * <p>
 * Keys are automatically generated using the pattern: {@code className:key}
 * where {@code className} is {@code T.class.getName()} and {@code key} is the
 * result of applying the {@code keyMapper} function.
 * </p>
 *
 * @param <T> the type of objects stored in the repository
 * @param <K> the type of keys used to identify objects
 * @author LeyCM
 * @version 1.1.2
 * @see RedisCache
 * @see Iterable
 */
@Data // No Class can not be a record because of extension in future
@SuppressWarnings("ClassCanBeRecord")
public class RedisRepository<T, K> implements Iterable<T> {

    protected final @NonNull RedisCache cache;
    protected final @NonNull Class<T> tClass;
    protected final @NonNull Function<K, String> keyMapper;

    /**
     * Constructs a new {@code RedisRepository} with the specified cache, type, and key mapper.
     *
     * @param cache     the Redis cache instance (must not be {@code null})
     * @param tClass    the class type of the stored objects (must not be {@code null})
     * @param keyMapper function to map keys of type {@code K} to string representations (must not be {@code null})
     * @throws NullPointerException if any parameter is {@code null}
     */
    public RedisRepository(
            final @NonNull RedisCache cache,
            final @NonNull Class<T> tClass,
            final @NonNull Function<K, String> keyMapper
    ) {
        this.cache = cache;
        this.tClass = tClass;
        this.keyMapper = keyMapper;
    }

    /**
     * Generates the Redis set key used to track all repository keys.
     * <p>
     * The set key follows the pattern: {@code className:keys}
     * where {@code className} is {@code T.class.getName()}.
     * </p>
     *
     * @return the Redis set key for tracking repository keys
     */
    private @NonNull String keySetName() {
        return tClass.getName() + ":keys";
    }

    /**
     * Retrieves an object from the repository by its key.
     *
     * @param key the object's key (must not be {@code null})
     * @return an {@link Optional} containing the object, or empty if not found
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public @NonNull Optional<T> get(final @NonNull K key) {
        return cache.get(repoKey(key), tClass);
    }

    /**
     * Stores an object in the repository with the specified key.
     * <p>
     * This method:
     * </p>
     * <ol>
     *   <li>Stores the object in Redis using the generated repository key</li>
     *   <li>Adds the generated key to the tracking set</li>
     * </ol>
     *
     * @param key   the object's key (must not be {@code null})
     * @param value the object to store (must not be {@code null})
     * @throws NullPointerException if {@code key} or {@code value} is {@code null}
     */
    public void set(final @NonNull K key, final @NonNull T value) {
        cache.set(repoKey(key), value);
        cache.add(keySetName(), repoKey(key));
    }

    /**
     * Retrieves an object from the repository or returns {@code null} if not found.
     *
     * @param key the object's key (must not be {@code null})
     * @return the object, or {@code null} if not found
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public @Nullable T getValue(final @NonNull K key) {
        return cache.get(repoKey(key), tClass).orElse(null);
    }

    /**
     * Deletes an object from the repository.
     * <p>
     * This method:
     * </p>
     * <ol>
     *   <li>Deletes the object from Redis</li>
     *   <li>Removes the key from the tracking set</li>
     * </ol>
     *
     * @param key the object's key to delete (must not be {@code null})
     * @throws NullPointerException if {@code key} is {@code null}
     */
    public void delete(final @NonNull K key) {
        cache.delete(repoKey(key));
        cache.rem(keySetName(), repoKey(key));
    }

    /**
     * Retrieves all keys currently stored in the repository.
     * <p>
     * The keys are extracted from the Redis tracking set and converted back
     * to their original type {@code K} using reverse key extraction.
     * </p>
     *
     * @return a {@link Set} containing all repository keys
     */
    public @NonNull Set<K> getKeys() {
        Set<String> storedKeys = cache.members(keySetName());
        return storedKeys.stream()
                .map(this::extractOriginalKey)
                .map(key -> (K) key)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * Generates the full Redis key for a repository object.
     * <p>
     * The key follows the pattern: {@code className:mappedKey}
     * where {@code className} is {@code T.class.getName()} and {@code mappedKey}
     * is the result of applying {@code keyMapper} to the original key.
     * </p>
     *
     * @param key the original key of type {@code K}
     * @return the full Redis key as a string
     */
    protected @NonNull String repoKey(final @NonNull K key) {
        return tClass.getName() + ":" + keyMapper.apply(key);
    }

    /**
     * Extracts the original key from a full Redis key.
     * <p>
     * This is the reverse operation of {@link #repoKey(Object)}.
     * </p>
     *
     * @param repoKey the full Redis key
     * @return the extracted original key as a string
     */
    protected @NonNull String extractOriginalKey(@NonNull String repoKey) {
        int idx = repoKey.indexOf(':');
        if (idx == -1 || idx + 1 >= repoKey.length()) return repoKey;
        return repoKey.substring(idx + 1);
    }

    /**
     * Returns an iterator over all objects in the repository.
     * <p>
     * The iterator lazily fetches objects as needed. Changes to the repository
     * during iteration may or may not be reflected in the iteration results.
     * </p>
     *
     * @return an {@link Iterator} over all objects in the repository
     */
    @Override
    public @NotNull Iterator<T> iterator() {
        return getKeys().stream()
                .map(this::getValue)
                .filter(Objects::nonNull)
                .iterator();
    }
}