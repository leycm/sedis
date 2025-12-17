package de.leycm.sedis.entries;

import de.leycm.sedis.pool.SedisResultPool;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface SedisEntry<T> {

    default @NonNull Optional<T> get() {
        return getPoot().kGet(getKey()).as(getClazz());
    }

    default @Nullable T getValue() {
        return getPoot().kGet(getKey()).as(getClazz()).orElse(null);
    }

    default void set(final @Nullable T value) {
        if (value == null) {
            delete();
            return;
        }

        getPoot().kSet(getKey(), value);
    }

    default void set(final @Nullable T value, long ttl) {
        if (value == null) {
            delete();
            return;
        }

        getPoot().kSet(getKey(), value, ttl);
    }

    default void delete() {
        getPoot().kDelete(getKey());
    }

    default boolean exists() {
        return getPoot().kExists(getKey());
    }

    default long ttl() {
        return getPoot().kTTL(getKey());
    }

    @NonNull SedisResultPool getPoot();

    @NonNull String getKey();

    @NonNull Class<T> getClazz();

}
