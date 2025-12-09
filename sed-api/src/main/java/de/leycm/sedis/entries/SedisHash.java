package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record SedisHash<K, V>(
        @NonNull SedisCache cache,
        @NonNull String key,
        @NonNull Class<V> vClass
) implements SedisEntry<V> {

    public void put(@NonNull K hashKey, @NonNull V value) {
        cache.hSet(key, hashKey, value);
    }

    public @NotNull Optional<V> get(@NonNull K hashKey) {
        return cache.hGet(key, hashKey, vClass);
    }

    public @NotNull Map<K, V> getAll() {
        return cache.hGetAll(key, vClass);
    }

    @SuppressWarnings("unchecked")
    public void remove(@NonNull K hashKey) {
        cache.hDelete(key, hashKey);
    }

    @SafeVarargs
    public final void remove(@NonNull K... hashKeys) {
        cache.hDelete(key, hashKeys);
    }

    public boolean containsKey(@NonNull K hashKey) {
        return cache.hExists(key, hashKey);
    }

    public long size() {
        return cache.hLen(key);
    }

    public int intSize() {
        return Long.valueOf(size()).intValue();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void clear() {
        cache.kDelete(key);
    }
}