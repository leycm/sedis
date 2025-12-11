package de.leycm.sedis.pubsub;

import com.google.gson.Gson;
import de.leycm.sedis.function.RedisSubscriber;
import lombok.NonNull;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class SedisPubSubManager {

    private final Map<String, List<RedisSubscriber<?>>> subscribers = new ConcurrentHashMap<>();
    private final JedisPool pool;
    private final Gson gson;

    private volatile SedisPubSubThread pubSubThread;

    public SedisPubSubManager(final @NonNull JedisPool pool, final @NonNull Gson gson) {
        this.pool = pool;
        this.gson = gson;
    }

    public <M> void subscribe(final @NonNull String channel, final @NonNull RedisSubscriber<M> subscriber) {
        final boolean wasEmpty = subscribers.isEmpty();
        subscribers.computeIfAbsent(channel, k -> new CopyOnWriteArrayList<>()).add(subscriber);

        if (wasEmpty) {
            start();
        } else if (pubSubThread != null) {
            pubSubThread.subscribeToChannel(channel);
        }
    }

    public <M> void unsubscribe(final @NonNull String channel, final @NonNull RedisSubscriber<M> subscriber) {
        final List<RedisSubscriber<?>> channelSubscribers = subscribers.get(channel);
        if (channelSubscribers == null) return;

        channelSubscribers.remove(subscriber);

        if (channelSubscribers.isEmpty()) {
            subscribers.remove(channel);
            if (pubSubThread != null) {
                pubSubThread.unsubscribeFromChannel(channel);
            }
        }

        if (subscribers.isEmpty()) {
            stop();
        }
    }

    public <M> void publish(final @NonNull String channel, final @NonNull M message) {
        try (final var jedis = pool.getResource()) {
            final String json = gson.toJson(message);
            jedis.publish(channel, json);
        }
    }

    public void start() {
        if (pubSubThread != null && pubSubThread.isAlive()) return;

        pubSubThread = new SedisPubSubThread(pool, gson, subscribers);
        pubSubThread.start();
    }

    public void stop() {
        if (pubSubThread != null) {
            pubSubThread.shutdown();
            pubSubThread = null;
        }
    }

    public boolean isRunning() {
        return pubSubThread != null && pubSubThread.isAlive();
    }

    List<RedisSubscriber<?>> getSubscribers(final String channel) {
        return subscribers.get(channel);
    }
}