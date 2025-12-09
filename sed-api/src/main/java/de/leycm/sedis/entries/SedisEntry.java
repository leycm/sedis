package de.leycm.sedis.entries;

import de.leycm.sedis.SedisCache;
import lombok.NonNull;

public interface SedisEntry<V> {

    @NonNull String key();

    @NonNull SedisCache cache();

}
