package de.leycm.sedis.pubsub;

import com.google.gson.Gson;
import de.leycm.sedis.function.RedisSubscriber;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class SedisPubSubThread extends Thread {

    private final JedisPool pool;
    private final Gson gson;
    private final Map<String, List<RedisSubscriber<?>>> subscribers;

    private volatile SedisPubSubListener pubSubListener;
    private volatile Jedis jedis;
    private final CountDownLatch startedLatch = new CountDownLatch(1);

    public SedisPubSubThread(final @NonNull JedisPool pool,
                             final @NonNull Gson gson,
                             final @NonNull Map<String, List<RedisSubscriber<?>>> subscribers) {
        super("RedisPubSub-Thread");
        this.pool = pool;
        this.gson = gson;
        this.subscribers = subscribers;
        setDaemon(true);
    }

    @Override
    public void run() {
        try {
            jedis = pool.getResource();
            pubSubListener = new SedisPubSubListener(subscribers);

            final String[] channels = subscribers.keySet().toArray(new String[0]);
            startedLatch.countDown();

            jedis.subscribe(pubSubListener, channels);
        } catch (final Exception e) {
            log.error("Redis PubSub thread error", e);
        } finally {
            closeResources();
        }
    }

    public void subscribeToChannel(final @NonNull String channel) {
        awaitStart();
        if (pubSubListener != null && pubSubListener.isSubscribed()) {
            pubSubListener.subscribe(channel);
        }
    }

    public void unsubscribeFromChannel(final @NonNull String channel) {
        awaitStart();
        if (pubSubListener != null && pubSubListener.isSubscribed()) {
            pubSubListener.unsubscribe(channel);
        }
    }

    public void shutdown() {
        if (pubSubListener != null && pubSubListener.isSubscribed()) {
            pubSubListener.unsubscribe();
        }

        interrupt();

        try {
            join(2000);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        closeResources();
    }

    private void awaitStart() {
        try {
            startedLatch.await(5, TimeUnit.SECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void closeResources() {
        if (jedis != null) {
            try {
                jedis.close();
            } catch (final Exception e) {
                log.error("Error closing Jedis connection", e);
            }
        }
    }
}