/**
 * LECP-LICENSE NOTICE
 * <br><br>
 * This Sourcecode is under the LECP-LICENSE. <br>
 * License at: <a href="https://github.com/leycm/leycm/blob/main/LICENSE">GITHUB</a>
 * <br><br>
 * Copyright (c) LeyCM <a href="mailto:leycm@proton.me">leycm@proton.me</a> l <br>
 * Copyright (c) maintainers <br>
 * Copyright (c) contributors
 */
package de.leycm.sedis;

import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record RedisEntry<T>(@NonNull RedisCache cache,
                            @NonNull Class<T> tClass,
                            @NonNull String key
) {

    public @NonNull Optional<T> get() {
        return cache.get(key, tClass);
    }

    public void set(final @NonNull T value) {
        cache.set(key, value);
    }

    public @Nullable T getValue() {
        return cache.get(key, tClass).orElse(null);
    }

    public void delete() {
        cache.delete(key);
    }

}
