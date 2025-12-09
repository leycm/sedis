package de.leycm.sedis;

import de.leycm.sedis.entries.*;
import lombok.NonNull;

import java.util.function.Function;

public interface SedisCache extends SedisPool {

    default <V> @NonNull SedisKey<V> key(final @NonNull String key,
                                         final @NonNull Class<V> vClass) {
        return new SedisKey<>(this, key, vClass);
    }

    default <K, V> @NonNull SedisHash<K, V> hash(final @NonNull String key,
                                                 final @NonNull Class<V> vClass) {
        return new SedisHash<>(this, key, vClass);
    }

    default <V> @NonNull SedisList<V> list(final @NonNull String key,
                                           final @NonNull Class<V> vClass) {
        return new SedisList<>(this, key, vClass);
    }


    default <K, V> @NonNull SedisRepo<K, V> repo(final @NonNull Function<K, String> keyMapper,
                                                 final @NonNull Function<String, K> keyReMapper,
                                                 final @NonNull Class<V> vClass) {
        return new SedisRepo<>(this, vClass) {
            @Override
            protected @NonNull String mapKey(@NonNull K key) {
                return keyMapper.apply(key);
            }

            @Override
            protected @NonNull K reMapKey(@NonNull String key) {
                return keyReMapper.apply(key);
            }
        };
    }

    default  <V> @NonNull SedisSet<V> set(final @NonNull String key,
                                          final @NonNull Class<V> vClass) {
        return new SedisSet<>(this, key, vClass);
    }

    default <V> @NonNull SedisZSet<V> sortedSet(final @NonNull String key,
                                           final @NonNull Class<V> vClass) {
        return new SedisZSet<>(this, key, vClass);
    }

}