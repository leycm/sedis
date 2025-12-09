package de.leycm.sedis.entries;

import lombok.NonNull;

public interface SedisEntry<V> {

    @NonNull String id();

}
