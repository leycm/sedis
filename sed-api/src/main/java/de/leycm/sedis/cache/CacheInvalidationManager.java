package de.leycm.sedis.cache;

import de.leycm.sedis.function.RedisSubscriber;
import de.leycm.sedis.pubsub.SedisPubSubManager;
import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class CacheInvalidationManager {

    private static final String INVALIDATE_CHANNEL = "sedis.cache:invalidate";
    private static final String CLEAR_CHANNEL = "sedis.cache:clear";

    private final Map<String, ScheduledFuture<?>> expirationTasks = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final SedisPubSubManager pubSubManager;
    private final CacheStorage cacheStorage;

    private final RedisSubscriber<String> invalidateSubscriber;
    private final RedisSubscriber<String> clearSubscriber;

    public CacheInvalidationManager(final @NonNull ScheduledExecutorService scheduler,
                                    final @NonNull SedisPubSubManager pubSubManager,
                                    final @NonNull CacheStorage cacheStorage) {
        this.scheduler = scheduler;
        this.pubSubManager = pubSubManager;
        this.cacheStorage = cacheStorage;

        this.invalidateSubscriber = (channel, message) -> invalidateKey(message);
        this.clearSubscriber = (channel, message) -> cacheStorage.clear();
    }

    public void initialize() {
        pubSubManager.subscribe(INVALIDATE_CHANNEL, invalidateSubscriber);
        pubSubManager.subscribe(CLEAR_CHANNEL, clearSubscriber);
    }

    public void shutdown() {
        pubSubManager.unsubscribe(INVALIDATE_CHANNEL, invalidateSubscriber);
        pubSubManager.unsubscribe(CLEAR_CHANNEL, clearSubscriber);

        cancelAllExpirationTasks();
    }

    public void invalidateKey(final @NonNull String key) {
        cacheStorage.invalidateKey(key);
        cancelExpirationTask(key);
    }

    public void publishInvalidation(final @NonNull String key) {
        pubSubManager.publish(INVALIDATE_CHANNEL, key);
    }

    public void publishClear() {
        pubSubManager.publish(CLEAR_CHANNEL, "");
    }

    public void scheduleExpiration(final @NonNull String key,
                                   final long seconds,
                                   final @NonNull Runnable onExpire) {
        cancelExpirationTask(key);

        final ScheduledFuture<?> task = scheduler.schedule(() -> {
            onExpire.run();
            expirationTasks.remove(key);
        }, seconds, TimeUnit.SECONDS);

        expirationTasks.put(key, task);
    }

    public void cancelExpirationTask(final @NonNull String key) {
        final ScheduledFuture<?> task = expirationTasks.remove(key);
        if (task != null) {
            task.cancel(false);
        }
    }

    public void cancelAllExpirationTasks() {
        expirationTasks.values().forEach(task -> task.cancel(false));
        expirationTasks.clear();
    }
}