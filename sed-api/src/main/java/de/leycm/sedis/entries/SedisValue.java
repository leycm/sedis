package de.leycm.sedis.entries;

import de.leycm.sedis.pool.SedisResultPool;
import lombok.NonNull;

public record SedisValue<T>(@NonNull String key,
                            @NonNull SedisResultPool pool,
                            @NonNull Class<T> tClass)
        implements SedisEntry<T> {

    @Override
    public @NonNull SedisResultPool getPoot() {
        return pool;
    }

    @Override
    public @NonNull String getKey() {
        return key;
    }

    @Override
    public @NonNull Class<T> getClazz() {
        return tClass;
    }

}