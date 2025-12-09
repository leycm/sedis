package de.leycm.sedis.function;

import de.leycm.sedis.SedisPool;

@FunctionalInterface
public interface SyncOperation<R> extends
        SyncPoolOperation<R, SedisPool> {
}