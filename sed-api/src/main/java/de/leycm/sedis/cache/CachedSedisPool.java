package de.leycm.sedis.cache;

import com.google.gson.Gson;
import de.leycm.sedis.pool.SedisResult;
import de.leycm.sedis.pool.SedisResultPool;
import de.leycm.sedis.pubsub.SedisPubSub;
import de.leycm.sedis.request.SedisRequest;
import de.leycm.sedis.request.SedisValueRequest;
import lombok.NonNull;
import redis.clients.jedis.Jedis;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CachedSedisPool implements SedisResultPool {
    private final Map<SedisRequest<?>, SedisResult<?>> cache = new ConcurrentHashMap<>();

    private final SedisPubSub pubSub;
    private final Jedis jedis;
    private final Gson gson;

    public CachedSedisPool(final @NonNull SedisPubSub pubSub,
                           final @NonNull Jedis jedis,
                           final @NonNull Gson gson) {
        this.pubSub = pubSub;
        this.jedis = jedis;
        this.gson = gson;
    }

    @Override
    @SuppressWarnings("unchecked") // it's not checked but forced
    public @NonNull SedisResult<SedisValueRequest> kGet(final @NonNull String key) {
        SedisValueRequest request = new SedisValueRequest(key);
        return (SedisResult<SedisValueRequest>) cache.computeIfAbsent(request, k
                -> new SedisResult<>(request, jedis.get(key), gson));
    }

    @Override
    public void kSet(final @NonNull String key,
                     final @NonNull Object value) {
        jedis.set(key, gson.toJson(value));
        pushInvalidate(new SedisValueRequest(key));
    }

    @Override
    public void kSet(final @NonNull String key,
                     final @NonNull Object value,
                     long ttl) {
        jedis.setex(key, ttl, gson.toJson(value));
        pushInvalidate(new SedisValueRequest(key));
    }

    @Override
    public void kDelete(final @NonNull String @NonNull ... keys) {
        for (String key : keys) {
            jedis.del(key);
            pushInvalidate(new SedisValueRequest(key));
        }
    }

    @Override
    public boolean kExists(final @NonNull String key) {
        SedisRequest<?> request = new SedisValueRequest(key);
        if (cache.containsKey(request)) return true;
        return jedis.exists(key);
    }

    @Override
    public void kExpire(final @NonNull String key,
                        long seconds) {
        jedis.expire(key, seconds);
    }

    @Override
    public long kTTL(final @NonNull String key) {
        return jedis.ttl(key);
    }

    @Override
    public void close() {
        jedis.close();
        cache.clear();
    }

    public void clear() {
        cache.clear();
    }

    public void invalidate(final @NonNull SedisRequest<?> request) {
        if (!cache.containsKey(request)) return;
        cache.get(request).clear();
        cache.remove(request);
    }

    private void pushInvalidate(final @NonNull SedisRequest<?> request) {
        // TODO: add pubsub invalidate
    }

}
