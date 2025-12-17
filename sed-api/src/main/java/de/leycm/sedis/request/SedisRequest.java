package de.leycm.sedis.request;

import lombok.NonNull;

public interface SedisRequest<T extends SedisRequest<T>> {

    @NonNull String[] toStrings();

    @NonNull String prefix();

    default @NonNull String asString(final @NonNull String delimiter) {
        return prefix() + '\\' + String.join(delimiter, toStrings());
    }

    default boolean equals(final @NonNull T request) {
        if (toStrings().length != request.toStrings().length) return false;
        return asString("\\").equals(request.asString("\\"));
    }

    @NonNull String toString();

    boolean equals(Object o);

    int hashCode();

}
