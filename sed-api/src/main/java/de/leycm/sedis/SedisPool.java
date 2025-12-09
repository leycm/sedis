package de.leycm.sedis;

import de.leycm.sedis.function.RedisSubscriber;
import de.leycm.sedis.function.SyncOperation;
import de.leycm.sedis.function.SyncPoolOperation;
import lombok.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public interface SedisPool {

    // ===== ASYNC RUNNER =====

    default <R> @NonNull CompletableFuture<R> async(@NonNull SyncOperation<R> operation) {
        return async(operation, SedisPool.class);
    }

    // can be used to use Impl functions
    <R, P extends SedisPool> @NonNull CompletableFuture<R> async(@NonNull SyncPoolOperation<R, P> operation,
                                                        @NonNull Class<P> pClass);

    // ===== KEY SYSTEM =====

    <V> @NonNull Optional<V> kGet(@NonNull String key,
                                  @NonNull Class<V> valueClass);

    <V> void kSet(@NonNull String key,
                  @NonNull V value);

    <V> void kSet(@NonNull String key,
                  @NonNull V value, long ttl);

    void kDelete(@NonNull String... keys);

    boolean kExists(@NonNull String key);

    void kExpire(@NonNull String key, long seconds);

    long kTTL(@NonNull String key);

    // ===== HASH SYSTEM =====

    <HK, HV> void hSet(@NonNull String key,
                       @NonNull HK hashKey,
                       @NonNull HV value);

    <HK, HV> @NonNull Optional<HV> hGet(@NonNull String key,
                               @NonNull HK hashKey,
                               @NonNull Class<HV> valueClass);

    <HK, HV> @NonNull Map<HK, HV> hGetAll(@NonNull String key,
                                 @NonNull Class<HV> valueClass);

    @SuppressWarnings("unchecked")
    <HK> void hDelete(@NonNull String key,
                      @NonNull HK... hashKeys);

    <HK> boolean hExists(@NonNull String key,
                         @NonNull HK hashKey);

    long hLen(@NonNull String key);

    // ===== SET =====

    @SuppressWarnings("unchecked")
    <V> void sAdd(@NonNull String key,
                  @NonNull V... values);

    <V> boolean sIsMember(@NonNull String key,
                          @NonNull V value);

    <V> @NonNull Set<V> sMembers(@NonNull String key,
                        @NonNull Class<V> valueClass);

    @SuppressWarnings("unchecked")
    <V> void sRemove(@NonNull String key,
                     @NonNull V... values);

    long sCard(@NonNull String key);

    // ===== SORTED SET SYSTEM =====

    <V> void zAdd(@NonNull String key, double score,
                  @NonNull V value);

    <V> void zAdd(@NonNull String key,
                  @NonNull Map<V, Double> values);

    <V> @NonNull Set<V> zRange(@NonNull String key, long start, long end,
                      @NonNull Class<V> valueClass);

    <V> @NonNull Set<V> zRevRange(@NonNull String key, long start, long end,
                         @NonNull Class<V> valueClass);

    long zCard(@NonNull String key);

    @SuppressWarnings("unchecked")
    <V> void zRemove(@NonNull String key,
                     @NonNull V... values);

    <V> @NonNull Optional<Double> zScore(@NonNull String key,
                      @NonNull V value);

    <V> @NonNull Optional<Long> zRank(@NonNull String key,
                   @NonNull V value);

    <V> @NonNull Optional<Long> zRevRank(@NonNull String key,
                      @NonNull V value);

    // ===== LIST SYSTEM =====

    @SuppressWarnings("unchecked")
    <V> void lPush(@NonNull String key, @NonNull V... values);

    @SuppressWarnings("unchecked")
    <V> void rPush(@NonNull String key, @NonNull V... values);

    <V> @NonNull Optional<V> lPop(@NonNull String key,
                         @NonNull Class<V> valueClass);

    <V> @NonNull Optional<V> rPop(@NonNull String key,
                         @NonNull Class<V> valueClass);

    <V> @NonNull List<V> lRange(@NonNull String key, long start, long end,
                       @NonNull Class<V> valueClass);

    long lLen(@NonNull String key);

    <V> void lSet(@NonNull String key, long index, @NonNull V value);

    <V> @NonNull Optional<V> lIndex(@NonNull String key, long index,
                           @NonNull Class<V> valueClass);

    // ===== PUB SUB SYSTEM =====

    <M> void publish(@NonNull String channel, @NonNull M message);

    <M> void subscribe(@NonNull String channel,
                       @NonNull RedisSubscriber<M> subscriber);

    <M> void unsubscribe(@NonNull String channel,
                         @NonNull RedisSubscriber<M> subscriber);

}