package de.leycm.sedis.function;

import de.leycm.sedis.SedisPool;

import java.util.function.Function;

@FunctionalInterface
public interface SyncPoolOperation<P extends SedisPool, R>
        extends Function<P, R> {
}
