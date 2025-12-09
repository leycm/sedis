package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.stream.Collectors;

public record SedisSet<V>(
        @NonNull SedisCache cache,
        @NonNull String key,
        @NonNull Class<V> vClass
) implements SedisEntry<V>, Set<V> {

    public void add(@NonNull Collection<? extends V> values) {
        if (!values.isEmpty()) {
            cache.sAdd(key, values.toArray(vClassArray(values.size())));
        }
    }

    public boolean isMember(@NonNull V value) {
        return cache.sIsMember(key, value);
    }

    public @NonNull Set<V> members() {
        return cache.sMembers(key, vClass);
    }

    public void remove(@NonNull Collection<? extends V> values) {
        if (!values.isEmpty()) {
            cache.sRemove(key, values.toArray(vClassArray(values.size())));
        }
    }

    public int size() {
        return Long.valueOf(longSize()).intValue();
    }

    public long longSize() {
        return cache.sCard(key);
    }

    @Override
    public boolean isEmpty() {
        return longSize() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return vClass.isInstance(o) && cache.sIsMember(key, vClass.cast(o));
    }

    @Override
    public @NonNull Iterator<V> iterator() {
        return members().iterator();
    }

    @Override
    public @NonNull Object @NonNull [] toArray() {
        return members().toArray();
    }

    @Override
    public @NonNull <T> T @NonNull [] toArray(@NonNull T @NonNull [] a) {
        return members().toArray(a);
    }

    @Override
    public boolean add(V value) {
        //noinspection unchecked
        cache.sAdd(key, value); ;
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (!vClass.isInstance(o)) return false;
        //noinspection unchecked
        cache.sRemove(key, vClass.cast(o));
        return true;
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> c) {
        return c.stream().allMatch(this::contains);
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends V> c) {
        add(c);
        return true;
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
        Set<V> retainSet = c.stream()
                .filter(vClass::isInstance)
                .map(vClass::cast)
                .collect(Collectors.toSet());

        boolean changed = false;
        for (V v : members()) {
            if (!retainSet.contains(v)) {
                remove(v);
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
        boolean changed = false;
        for (Object o : c) {
            if (remove(o)) changed = true;
        }
        return changed;
    }

    @Override
    public void clear() {
        cache.kDelete(key);
    }

    @SuppressWarnings("unchecked")
    private V[] vClassArray(int size) {
        return (V[]) java.lang.reflect.Array.newInstance(vClass, size);
    }
}
