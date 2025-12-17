package de.leycm.sedis.pool;

import de.leycm.sedis.request.SedisValueRequest;
import lombok.NonNull;

public interface SedisResultPool extends AutoCloseable{

    @NonNull SedisResult<SedisValueRequest> kGet(@NonNull String key);

    void kSet(@NonNull String key, @NonNull Object value);

    void kSet(@NonNull String key, @NonNull Object value, long ttl);

    void kDelete(@NonNull String @NonNull ... keys);

    boolean kExists(@NonNull String key);

    void kExpire(@NonNull String key, long seconds);

    long kTTL(@NonNull String key);

}
