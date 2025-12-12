package de.leycm.sedis.cache;

import com.google.gson.Gson;
import de.leycm.neck.instance.Initializable;
import de.leycm.sedis.SedisCache;
import de.leycm.sedis.SedisPool;
import de.leycm.sedis.builder.LocalSedisCacheBuilder;
import de.leycm.sedis.function.RedisSubscriber;
import de.leycm.sedis.function.SyncPoolOperation;
import de.leycm.sedis.pubsub.SedisPubSubManager;
import lombok.NonNull;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class LocalSedisCache implements SedisCache, Initializable {

    private final LocalCacheStorage cacheStorage;
    private final SedisPubSubManager pubSubManager;
    private final CacheInvalidationManager invalidationManager;

    private final ScheduledExecutorService scheduler;
    private final Executor executor;
    private final JedisPool pool;
    private final Gson gson;

    @Contract(" -> new")
    public static @NonNull LocalSedisCacheBuilder builder() {
        return LocalSedisCacheBuilder.builder();
    }

    public LocalSedisCache(final @NonNull ScheduledExecutorService scheduler,
                           final @NonNull Executor executor,
                           final @NonNull JedisPool pool,
                           final @NonNull Gson gson) {
        this.scheduler = scheduler;
        this.executor = executor;
        this.pool = pool;
        this.gson = gson;

        this.cacheStorage = new LocalCacheStorage();
        this.pubSubManager = new SedisPubSubManager(pool, gson);
        this.invalidationManager = new CacheInvalidationManager(scheduler, pubSubManager, cacheStorage);
    }

    @Override
    public void onInstall() {
        invalidationManager.initialize();
        pubSubManager.start();
    }

    @Override
    public void onUninstall() {
        invalidationManager.shutdown();
        pubSubManager.stop();

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (final InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        cacheStorage.clear();
    }

    @Override
    public void clear() {
        cacheStorage.clear();
        invalidationManager.cancelAllExpirationTasks();
    }

    @Override
    public @NonNull <P extends SedisPool, R> CompletableFuture<R> async(
            final @NonNull SyncPoolOperation<P, R> operation,
            final @NonNull Class<P> pClass) {
        if (!pClass.isInstance(this)) {
            throw new UnsupportedOperationException("<P> has to be the class or a super class from the pool");
        }
        @SuppressWarnings("unchecked")
        final P poolInstance = (P) this;
        return CompletableFuture.supplyAsync(() -> operation.apply(poolInstance), executor);
    }

    @Override
    public @NonNull <V> Optional<V> kGet(final @NonNull String key, final @NonNull Class<V> valueClass) {
        final V value = getFromCacheOrRedis(key, valueClass, jedis -> jedis.get(key));
        return Optional.ofNullable(value);
    }

    @Override
    public <V> void kSet(final @NonNull String key, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            jedis.set(key, json);

            cacheStorage.putRaw(key, json);
            cacheStorage.putTyped(cacheStorage.buildTypedKey(key, value.getClass()), value);
            invalidationManager.cancelExpirationTask(key);
        }
    }

    @Override
    public <V> void kSet(final @NonNull String key, final @NonNull V value, final long ttl) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            jedis.setex(key, ttl, json);

            cacheStorage.putRaw(key, json);
            cacheStorage.putTyped(cacheStorage.buildTypedKey(key, value.getClass()), value);
            scheduleExpirationCheck(key, ttl);
        }
    }

    @Override
    public void kDelete(final @NonNull String... keys) {
        try (final Jedis jedis = pool.getResource()) {
            jedis.del(keys);
            for (final String key : keys) {
                invalidationManager.invalidateKey(key);
                invalidationManager.publishInvalidation(key);
            }
        }
    }

    @Override
    public boolean kExists(final @NonNull String key) {
        try (final Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    @Override
    public void kExpire(final @NonNull String key, final long seconds) {
        try (final Jedis jedis = pool.getResource()) {
            jedis.expire(key, seconds);
            scheduleExpirationCheck(key, seconds);
        }
    }

    @Override
    public long kTTL(final @NonNull String key) {
        try (final Jedis jedis = pool.getResource()) {
            return jedis.ttl(key);
        }
    }

    @Override
    public <HK, HV> void hSet(final @NonNull String key, final @NonNull HK hashKey, final @NonNull HV value) {
        try (final Jedis jedis = pool.getResource()) {
            final String hkJson = gson.toJson(hashKey);
            final String hvJson = gson.toJson(value);
            jedis.hset(key, hkJson, hvJson);
        }
    }

    @Override
    public @NonNull <HK, HV> Optional<HV> hGet(final @NonNull String key, final @NonNull HK hashKey, final @NonNull Class<HV> valueClass) {
        final String hkJson = gson.toJson(hashKey);
        final String compositeKey = cacheStorage.buildCompositeKey(key, hkJson);
        final HV value = getFromCacheOrRedis(compositeKey, valueClass, jedis -> jedis.hget(key, hkJson));
        return Optional.ofNullable(value);
    }

    @Override
    public @NonNull <HK, HV> Map<HK, HV> hGetAll(final @NonNull String key, final @NonNull Class<HV> valueClass) {
        try (final Jedis jedis = pool.getResource()) {
            final Map<String, String> rawMap = jedis.hgetAll(key);
            final Map<HK, HV> result = new HashMap<>();
            for (final Map.Entry<String, String> entry : rawMap.entrySet()) {
                @SuppressWarnings("unchecked")
                final HK hk = (HK) gson.fromJson(entry.getKey(), Object.class);
                final HV hv = gson.fromJson(entry.getValue(), valueClass);
                result.put(hk, hv);
            }
            return result;
        }
    }

    @Override
    @SafeVarargs
    public final <HK> void hDelete(final @NonNull String key, final @NonNull HK... hashKeys) {
        try (final Jedis jedis = pool.getResource()) {
            final String[] hkJsons = Arrays.stream(hashKeys)
                    .map(gson::toJson)
                    .toArray(String[]::new);
            jedis.hdel(key, hkJsons);

            for (final String hkJson : hkJsons) {
                final String compositeKey = cacheStorage.buildCompositeKey(key, hkJson);
                cacheStorage.invalidateKey(compositeKey);
            }
        }
    }

    @Override
    public <HK> boolean hExists(final @NonNull String key, final @NonNull HK hashKey) {
        try (final Jedis jedis = pool.getResource()) {
            final String hkJson = gson.toJson(hashKey);
            return jedis.hexists(key, hkJson);
        }
    }

    @Override
    public long hLen(final @NonNull String key) {
        try (final Jedis jedis = pool.getResource()) {
            return jedis.hlen(key);
        }
    }

    @Override
    @SafeVarargs
    public final <V> void sAdd(final @NonNull String key, final @NonNull V... values) {
        try (final Jedis jedis = pool.getResource()) {
            final String[] jsonValues = Arrays.stream(values)
                    .map(gson::toJson)
                    .toArray(String[]::new);
            jedis.sadd(key, jsonValues);
        }
    }

    @Override
    public <V> boolean sIsMember(final @NonNull String key, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            return jedis.sismember(key, json);
        }
    }

    @Override
    public @NonNull <V> Set<V> sMembers(final @NonNull String key, final @NonNull Class<V> valueClass) {
        final String compositeKey = cacheStorage.buildCompositeKey(key, "set", valueClass.getName());

        final Set<V> cached = cacheStorage.getTyped(compositeKey);
        if (cached != null) return cached;

        try (final Jedis jedis = pool.getResource()) {
            final Set<String> members = jedis.smembers(key);
            final Set<V> result = members.stream()
                    .map(json -> gson.fromJson(json, valueClass))
                    .collect(Collectors.toSet());
            cacheStorage.putTyped(compositeKey, result);
            return result;
        }
    }

    @Override
    @SafeVarargs
    public final <V> void sRemove(final @NonNull String key, final @NonNull V... values) {
        try (final Jedis jedis = pool.getResource()) {
            final String[] jsonValues = Arrays.stream(values)
                    .map(gson::toJson)
                    .toArray(String[]::new);
            jedis.srem(key, jsonValues);
            cacheStorage.invalidateByPrefix(key + ":set:");
        }
    }

    @Override
    public long sCard(final @NonNull String key) {
        try (final Jedis jedis = pool.getResource()) {
            return jedis.scard(key);
        }
    }

    @Override
    public <V> void zAdd(final @NonNull String key, final double score, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            jedis.zadd(key, score, json);
            cacheStorage.invalidateByPrefix(key + ":zset:");
        }
    }

    @Override
    public <V> void zAdd(final @NonNull String key, final @NonNull Map<V, Double> values) {
        try (final Jedis jedis = pool.getResource()) {
            final Map<String, Double> scoreMembers = values.entrySet().stream()
                    .collect(Collectors.toMap(
                            e -> gson.toJson(e.getKey()),
                            Map.Entry::getValue
                    ));
            jedis.zadd(key, scoreMembers);
            cacheStorage.invalidateByPrefix(key + ":zset:");
        }
    }

    @Override
    public @NonNull <V> List<V> zRange(final @NonNull String key, final long start, final long end, final @NonNull Class<V> valueClass) {
        final String compositeKey = cacheStorage.buildCompositeKey(key, "zset", "range", String.valueOf(start), String.valueOf(end), valueClass.getName());

        final List<V> cached = cacheStorage.getTyped(compositeKey);
        if (cached != null) return cached;

        try (final Jedis jedis = pool.getResource()) {
            final List<String> range = jedis.zrange(key, start, end);
            final List<V> result = range.stream()
                    .map(json -> gson.fromJson(json, valueClass))
                    .collect(Collectors.toList());
            cacheStorage.putTyped(compositeKey, result);
            return result;
        }
    }

    @Override
    public @NonNull <V> List<V> zRevRange(final @NonNull String key, final long start, final long end, final @NonNull Class<V> valueClass) {
        final String compositeKey = cacheStorage.buildCompositeKey(key, "zset", "revrange", String.valueOf(start), String.valueOf(end), valueClass.getName());

        final List<V> cached = cacheStorage.getTyped(compositeKey);
        if (cached != null) return cached;

        try (final Jedis jedis = pool.getResource()) {
            final List<String> range = jedis.zrevrange(key, start, end);
            final List<V> result = range.stream()
                    .map(json -> gson.fromJson(json, valueClass))
                    .collect(Collectors.toList());
            cacheStorage.putTyped(compositeKey, result);
            return result;
        }
    }

    @Override
    public long zCard(final @NonNull String key) {
        try (final Jedis jedis = pool.getResource()) {
            return jedis.zcard(key);
        }
    }

    @Override
    @SafeVarargs
    public final <V> void zRemove(final @NonNull String key, final @NonNull V... values) {
        try (final Jedis jedis = pool.getResource()) {
            final String[] jsonValues = Arrays.stream(values)
                    .map(gson::toJson)
                    .toArray(String[]::new);
            jedis.zrem(key, jsonValues);
            cacheStorage.invalidateByPrefix(key + ":zset:");
        }
    }

    @Override
    public @NonNull <V> Optional<Double> zScore(final @NonNull String key, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            return Optional.ofNullable(jedis.zscore(key, json));
        }
    }

    @Override
    public @NonNull <V> Optional<Long> zRank(final @NonNull String key, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            return Optional.ofNullable(jedis.zrank(key, json));
        }
    }

    @Override
    public @NonNull <V> Optional<Long> zRevRank(final @NonNull String key, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            return Optional.ofNullable(jedis.zrevrank(key, json));
        }
    }

    @Override
    @SafeVarargs
    public final <V> void lPush(final @NonNull String key, final @NonNull V... values) {
        try (final Jedis jedis = pool.getResource()) {
            final String[] jsonValues = Arrays.stream(values)
                    .map(gson::toJson)
                    .toArray(String[]::new);
            jedis.lpush(key, jsonValues);
            cacheStorage.invalidateByPrefix(key + ":list:");
        }
    }

    @Override
    @SafeVarargs
    public final <V> void rPush(final @NonNull String key, final @NonNull V... values) {
        try (final Jedis jedis = pool.getResource()) {
            final String[] jsonValues = Arrays.stream(values)
                    .map(gson::toJson)
                    .toArray(String[]::new);
            jedis.rpush(key, jsonValues);
            cacheStorage.invalidateByPrefix(key + ":list:");
        }
    }

    @Override
    public @NonNull <V> Optional<V> lPop(final @NonNull String key, final @NonNull Class<V> valueClass) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = jedis.lpop(key);
            if (json == null) return Optional.empty();

            cacheStorage.invalidateByPrefix(key + ":list:");
            return Optional.ofNullable(gson.fromJson(json, valueClass));
        }
    }

    @Override
    public @NonNull <V> Optional<V> rPop(final @NonNull String key, final @NonNull Class<V> valueClass) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = jedis.rpop(key);
            if (json == null) return Optional.empty();

            cacheStorage.invalidateByPrefix(key + ":list:");
            return Optional.ofNullable(gson.fromJson(json, valueClass));
        }
    }

    @Override
    public @NonNull <V> List<V> lRange(final @NonNull String key, final long start, final long end, final @NonNull Class<V> valueClass) {
        final String compositeKey = cacheStorage.buildCompositeKey(key, "list", "range", String.valueOf(start), String.valueOf(end), valueClass.getName());

        final List<V> cached = cacheStorage.getTyped(compositeKey);
        if (cached != null) return cached;

        try (final Jedis jedis = pool.getResource()) {
            final List<String> range = jedis.lrange(key, start, end);
            final List<V> result = range.stream()
                    .map(json -> gson.fromJson(json, valueClass))
                    .collect(Collectors.toList());
            cacheStorage.putTyped(compositeKey, result);
            return result;
        }
    }

    @Override
    public long lLen(final @NonNull String key) {
        try (final Jedis jedis = pool.getResource()) {
            return jedis.llen(key);
        }
    }

    @Override
    public <V> void lSet(final @NonNull String key, final long index, final @NonNull V value) {
        try (final Jedis jedis = pool.getResource()) {
            final String json = gson.toJson(value);
            jedis.lset(key, index, json);
            cacheStorage.invalidateByPrefix(key + ":list:");
        }
    }

    @Override
    public @NonNull <V> Optional<V> lIndex(final @NonNull String key, final long index, final @NonNull Class<V> valueClass) {
        final String compositeKey = cacheStorage.buildCompositeKey(key, "list", "index", String.valueOf(index), valueClass.getName());

        final V cached = cacheStorage.getTyped(compositeKey);
        if (cached != null) return Optional.of(cached);

        try (final Jedis jedis = pool.getResource()) {
            final String json = jedis.lindex(key, index);
            if (json == null) return Optional.empty();

            final V value = gson.fromJson(json, valueClass);
            cacheStorage.putTyped(compositeKey, value);
            return Optional.of(value);
        }
    }

    @Override
    public <M> void publish(final @NonNull String channel, final @NonNull M message) {
        pubSubManager.publish(channel, message);
    }

    @Override
    public <M> void subscribe(final @NonNull String channel, final @NonNull RedisSubscriber<M> subscriber) {
        pubSubManager.subscribe(channel, subscriber);
    }

    @Override
    public <M> void unsubscribe(final @NonNull String channel, final @NonNull RedisSubscriber<M> subscriber) {
        pubSubManager.unsubscribe(channel, subscriber);
    }

    private void scheduleExpirationCheck(final @NonNull String key, final long seconds) {
        invalidationManager.scheduleExpiration(key, seconds, () -> {
            try (final Jedis jedis = pool.getResource()) {
                if (!jedis.exists(key)) {
                    invalidationManager.invalidateKey(key);
                    invalidationManager.publishInvalidation(key);
                }
            }
        });
    }

    @FunctionalInterface
    private interface RedisGetter<V> {
        String get(Jedis jedis);
    }

    private <V> @Nullable V getFromCacheOrRedis(final @NonNull String key,
                                                final @NonNull Class<V> valueClass,
                                                final @NonNull RedisGetter<V> redisGetter) {
        final String cacheKey = cacheStorage.buildTypedKey(key, valueClass);
        final V cached = cacheStorage.getTyped(cacheKey);
        if (cached != null) return cached;

        final String rawJson = cacheStorage.getRaw(key);
        if (rawJson != null) {
            final V value = gson.fromJson(rawJson, valueClass);
            cacheStorage.putTyped(cacheKey, value);
            return value;
        }

        try (final Jedis jedis = pool.getResource()) {
            final String json = redisGetter.get(jedis);
            if (json == null) return null;

            final V value = gson.fromJson(json, valueClass);
            cacheStorage.putRaw(key, json);
            cacheStorage.putTyped(cacheKey, value);
            return value;
        }
    }
}