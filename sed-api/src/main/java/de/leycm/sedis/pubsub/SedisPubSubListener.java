package de.leycm.sedis.pubsub;

import de.leycm.sedis.function.RedisSubscriber;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.JedisPubSub;

import java.util.List;
import java.util.Map;

@Slf4j
public class SedisPubSubListener extends JedisPubSub {

    private final Map<String, List<RedisSubscriber<?>>> subscribers;

    public SedisPubSubListener(final @NonNull Map<String, List<RedisSubscriber<?>>> subscribers) {
        this.subscribers = subscribers;
    }

    @Override
    public void onMessage(final String channel, final String message) {
        final List<RedisSubscriber<?>> channelSubscribers = subscribers.get(channel);
        if (channelSubscribers == null || channelSubscribers.isEmpty()) return;

        for (final RedisSubscriber<?> subscriber : channelSubscribers) {
            try {
                notifySubscriber(subscriber, channel, message);
            } catch (final Exception e) {
                log.error("Error notifying subscriber for channel: {}", channel, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void notifySubscriber(final RedisSubscriber<?> subscriber,
                                  final String channel,
                                  final String message) {
        final RedisSubscriber<String> stringSubscriber = (RedisSubscriber<String>) subscriber;
        stringSubscriber.onMessage(channel, message);
    }

    @Override
    public void onSubscribe(final String channel, final int subscribedChannels) {
        log.debug("Subscribed to channel: {} (total: {})", channel, subscribedChannels);
    }

    @Override
    public void onUnsubscribe(final String channel, final int subscribedChannels) {
        log.debug("Unsubscribed from channel: {} (remaining: {})", channel, subscribedChannels);
    }
}