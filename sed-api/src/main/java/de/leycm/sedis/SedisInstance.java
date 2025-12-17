package de.leycm.sedis;


import de.leycm.sedis.pool.SedisResultPool;
import de.leycm.sedis.pubsub.SedisPubSub;
import de.leycm.sedis.stream.SedisStreamer;
import lombok.NonNull;
import lombok.experimental.Delegate;

@SuppressWarnings("ClassCanBeRecord") // for later overwrite
public class SedisInstance implements SedisResultPool, SedisPubSub, SedisStreamer, AutoCloseable  {

    private final @Delegate SedisResultPool pool;
    private final @Delegate SedisPubSub pubSub;
    private final @Delegate SedisPubSub streamer;

    public SedisInstance(final @NonNull SedisResultPool pool,
                         final @NonNull SedisPubSub pubSub,
                         final @NonNull SedisPubSub streamer) {
        this.pool = pool;
        this.pubSub = pubSub;
        this.streamer = streamer;
    }

    @Override
    public void close() throws Exception {
        pool.close();
    }

}