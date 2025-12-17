package de.leycm.sedis.request;

import lombok.NonNull;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record SedisValueRequest(@NonNull String key)
        implements SedisRequest<SedisValueRequest> {

    @Override
    @Contract(value = " -> new", pure = true)
    public @NonNull String @NotNull [] toStrings() {
        return new String[]{key};
    }

    @Override
    public @NonNull String prefix() {
        return "value";
    }

    @Override
    public @NotNull String toString() {
        return asString("\\");
    }

    @Override
    public boolean equals(final @Nullable Object o) {
        if (o == null) return false;
        if (!(o instanceof SedisValueRequest request)) return false;
        return equals(request);
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

}
