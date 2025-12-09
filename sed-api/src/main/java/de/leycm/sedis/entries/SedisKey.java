package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record SedisKey<V>(@NonNull SedisCache cache,
                          @NonNull String key,
                          @NonNull Class<V> vClass
) implements SedisEntry<V> {

    public @NonNull Optional<V> get() {
        return cache.kGet(key, vClass);
    }

    public @Nullable V getValue() {
        return get().orElse(null);
    }

    public void set(final @NonNull V value) {
        cache.kSet(key, value);
    }

    @Override
    public @NonNull String key() {
        return key;
    }

}
