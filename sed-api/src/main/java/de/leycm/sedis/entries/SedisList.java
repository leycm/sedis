package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public record SedisList<V>(
        @NonNull SedisCache cache,
        @NonNull String key,
        @NonNull Class<V> vClass
) implements SedisEntry<V> {

    @SuppressWarnings("unchecked")
    public void leftPush(@NonNull V value) {
        cache.lPush(key, value);
    }

    @SafeVarargs
    public final void leftPush(@NonNull V... values) {
        cache.lPush(key, values);
    }

    @SuppressWarnings("unchecked")
    public void rightPush(@NonNull V value) {
        cache.rPush(key, value);
    }

    @SafeVarargs
    public final void rightPush(@NonNull V... values) {
        cache.rPush(key, values);
    }

    public @NotNull Optional<V> leftPop() {
        return cache.lPop(key, vClass);
    }

    public @NotNull Optional<V> rightPop() {
        return cache.rPop(key, vClass);
    }

    public @NotNull List<V> range(long start, long end) {
        return cache.lRange(key, start, end, vClass);
    }

    public @NotNull List<V> all() {
        return range(0, -1);
    }

    public long size() {
        return cache.lLen(key);
    }

    public int intSize() {
        return Long.valueOf(size()).intValue();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void set(long index, @NonNull V value) {
        cache.lSet(key, index, value);
    }

    public @NotNull Optional<V> get(long index) {
        return cache.lIndex(key, index, vClass);
    }

    public void clear() {
        cache.kDelete(key);
    }
}