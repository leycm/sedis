package de.leycm.sedis.cache;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CacheStorage {

    private final Map<String, String> rawCache = new ConcurrentHashMap<>();
    private final Map<String, Object> typedCache = new ConcurrentHashMap<>();

    public void putRaw(final @NonNull String key, final @NonNull String json) {
        rawCache.put(key, json);
    }

    public @Nullable String getRaw(final @NonNull String key) {
        return rawCache.get(key);
    }

    public void putTyped(final @NonNull String key, final @NonNull Object value) {
        typedCache.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <V> @Nullable V getTyped(final @NonNull String key) {
        return (V) typedCache.get(key);
    }

    public void invalidateKey(final @NonNull String key) {
        rawCache.remove(key);
        typedCache.keySet().removeIf(k -> k.startsWith(key + ":"));
    }

    public void invalidateByPrefix(final @NonNull String prefix) {
        typedCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clear() {
        rawCache.clear();
        typedCache.clear();
    }

    public String buildTypedKey(final @NonNull String key, final @NonNull Class<?> valueClass) {
        return key + ":" + valueClass.getName();
    }

    public String buildCompositeKey(final @NonNull String... parts) {
        return String.join(":", parts);
    }
}