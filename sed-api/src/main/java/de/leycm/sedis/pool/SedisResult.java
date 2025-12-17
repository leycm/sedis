package de.leycm.sedis.pool;

import com.google.gson.Gson;
import de.leycm.sedis.request.SedisRequest;
import lombok.Getter;
import lombok.NonNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class SedisResult<R extends SedisRequest<R>> {
    private final Map<Class<?>, Object> cache = new ConcurrentHashMap<>();

    private final Gson gson;

    private final R key;
    private String value;

    public SedisResult(final @NonNull R key,
                       final @Nullable String value,
                       final @NonNull Gson gson
    ) {
        this.gson = gson;
        this.key = key;
        this.value = value;
    }

    public @NonNull Optional<String> asString() {
        return Optional.ofNullable(value);
    }

    // should check for cached and only if absent create a new one
    // !! Important results are pointer to an object shared to all requests;
    public <T> @NonNull Optional<T> as(final @NonNull Class<T> tClass) {
        if (!isPresent()) return Optional.empty();
        return Optional.of(tClass.cast(cache.computeIfAbsent(tClass,
                key -> asNew(tClass))));
    }

    // should create a new instance
    public <T> @NonNull Optional<T> asNew(final @NonNull Class<T> tClass) {
        if (!isPresent()) return Optional.empty();
        return Optional.of(gson.fromJson(value, tClass));
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isPresent() {
        return value != null;
    }


    public void clear() {
        cache.clear();
        value = null;
    }


}
