package de.leycm.sedis.function;

import lombok.NonNull;

@FunctionalInterface
public interface RedisSubscriber<M> {
    void onMessage(@NonNull String channel, @NonNull M message);
}
