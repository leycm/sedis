package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public abstract class SedisRepo<K, V>
        implements SedisEntry<V>, Iterable<V> {

    protected final SedisCache cache;
    protected final Class<V> vClass;


    public SedisRepo(
            final @NonNull SedisCache cache,
            final @NonNull Class<V> vClass
    ) {
        this.cache = cache;
        this.vClass = vClass;
    }


    @Override
    public @NonNull String key() {
        return vClass.getName();
    }

    @Override
    public @NonNull SedisCache cache() {
        return cache;
    }

    public @NonNull Optional<V> get(final @NonNull K key) {
        return cache.kGet(repoKey(key), vClass);
    }

    public @Nullable V getValue(final @NonNull K key) {
        return get(key).orElse(null);
    }

    public void set(final @NonNull K key,
                    final @NonNull V value) {
        cache.kSet(repoKey(key), value);
        cache.sAdd(keySetName(), repoKey(key));
    }

    public void delete(final @NonNull K key) {
        cache.kDelete(repoKey(key));
        cache.sRemove(keySetName(), repoKey(key));
    }

    public @NonNull Set<K> getKeys() {
        Set<String> storedKeys = cache.sMembers(keySetName(), String.class);
        return storedKeys.stream()
                .map(this::reRepoKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public @NonNull Iterator<V> iterator() {
        return getKeys().stream()
                .map(this::getValue)
                .filter(Objects::nonNull)
                .iterator();
    }

    protected abstract @NonNull String mapKey(@NonNull K key);

    protected abstract @NonNull K reMapKey(@NonNull String key);


    protected @NonNull String repoKey(final @NonNull K key) {
        return vClass.getName() + ":" + mapKey(key);
    }

    protected @NonNull K reRepoKey(final @NonNull String repoKey) {
        int idx = repoKey.indexOf(':');
        return reMapKey(repoKey.substring(idx + 1));
    }

    protected @NonNull String keySetName() {
        return vClass.getName() + ":keys";
    }

}
