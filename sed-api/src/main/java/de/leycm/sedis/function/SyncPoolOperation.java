package de.leycm.sedis.function;

import de.leycm.sedis.SedisPool;

import java.util.function.Function;

@FunctionalInterface
public interface SyncPoolOperation<R, P extends SedisPool> 
        extends Function<R, P> {
}
