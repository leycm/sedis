package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Array;
import java.util.*;

public record SedisZSet<V>(
        @NonNull SedisCache cache,
        @NonNull String key,
        @NonNull Class<V> vClass
) implements SedisEntry<V> {

    public void add(double score, @NonNull V value) {
        cache.zAdd(key, score, value);
    }

    public void add(@NonNull Map<V, Double> values) {
        if (!values.isEmpty()) {
            cache.zAdd(key, values);
        }
    }

    public @NotNull List<V> range(long start, long end) {
        return cache.zRange(key, start, end, vClass);
    }

    public @NotNull List<V> revRange(long start, long end) {
        return cache.zRevRange(key, start, end, vClass);
    }

    public long size() {
        return cache.zCard(key);
    }

    public int intSize() {
        return Long.valueOf(size()).intValue();
    }

    public boolean isEmpty() {
        return size() == 0;
    }

    public void remove(@NonNull Collection<V> values) {
        if (!values.isEmpty())
            cache.zRemove(key, values.toArray(vClassArray(values.size())));
    }

    @SuppressWarnings("unchecked")
    public void remove(@NonNull V value) {
        cache.zRemove(key, value);
    }

    public @NotNull Optional<Double> score(@NonNull V value) {
        return cache.zScore(key, value);
    }

    public @NotNull Optional<Long> rank(@NonNull V value) {
        return cache.zRank(key, value);
    }

    public @NotNull Optional<Long> revRank(@NonNull V value) {
        return cache.zRevRank(key, value);
    }

    public boolean contains(@NonNull V value) {
        return score(value).isPresent();
    }

    public void clear() {
        cache.kDelete(key);
    }

    @SuppressWarnings("unchecked")
    private V[] vClassArray(int size) {
        return (V[]) Array.newInstance(vClass, size);
    }
}