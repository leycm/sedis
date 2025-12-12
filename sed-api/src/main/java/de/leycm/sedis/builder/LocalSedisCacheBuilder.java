package de.leycm.sedis.builder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.leycm.sedis.cache.LocalSedisCache;
import lombok.NonNull;
import org.jetbrains.annotations.Contract;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class LocalSedisCacheBuilder {

    private String host = "localhost";
    private int port = 6379;
    private String password = null;
    private int database = 0;
    private Duration timeout = Duration.ofSeconds(2);

    private int maxTotal = 8;
    private int maxIdle = 8;
    private int minIdle = 0;
    private boolean testOnBorrow = true;
    private boolean testOnReturn = false;
    private boolean testWhileIdle = false;
    private Duration timeBetweenEvictionRuns = Duration.ofSeconds(30);
    private int numTestsPerEvictionRun = 3;
    private Duration minEvictableIdleTime = Duration.ofMinutes(1);
    private Duration softMinEvictableIdleTime = Duration.ofMinutes(1);
    private boolean blockWhenExhausted = true;
    private Duration maxWaitTime = Duration.ofSeconds(2);

    private int schedulerThreads = 2;
    private int executorThreads = 4;
    private String threadNamePrefix = "sedis-cache";

    private Gson gson = null;
    private boolean prettyPrintJson = false;
    private boolean serializeNulls = false;

    private ScheduledExecutorService customScheduler = null;
    private Executor customExecutor = null;
    private JedisPool customJedisPool = null;

    private LocalSedisCacheBuilder() {
    }

    @Contract(" -> new")
    public static @NonNull LocalSedisCacheBuilder builder() {
        return new LocalSedisCacheBuilder();
    }

    public @NonNull LocalSedisCacheBuilder forAddress(final @NonNull String host, final int port) {
        this.host = host;
        this.port = port;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder forHost(final @NonNull String host) {
        this.host = host;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withPort(final int port) {
        this.port = port;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withPassword(final @NonNull String password) {
        this.password = password;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withDatabase(final int database) {
        if (database < 0 || database > 15) {
            throw new IllegalArgumentException("Database index must be between 0 and 15");
        }
        this.database = database;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withTimeout(final @NonNull Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withMaxConnections(final int maxConnections) {
        this.maxTotal = maxConnections;
        this.maxIdle = maxConnections;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withMaxTotal(final int maxTotal) {
        this.maxTotal = maxTotal;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withMaxIdle(final int maxIdle) {
        this.maxIdle = maxIdle;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withMinIdle(final int minIdle) {
        this.minIdle = minIdle;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withTestOnBorrow(final boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withTestOnReturn(final boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withTestWhileIdle(final boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withTimeBetweenEvictionRuns(final @NonNull Duration duration) {
        this.timeBetweenEvictionRuns = duration;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withMinEvictableIdleTime(final @NonNull Duration duration) {
        this.minEvictableIdleTime = duration;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withBlockWhenExhausted(final boolean blockWhenExhausted) {
        this.blockWhenExhausted = blockWhenExhausted;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withMaxWaitTime(final @NonNull Duration duration) {
        this.maxWaitTime = duration;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withSchedulerThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("Scheduler threads must be at least 1");
        }
        this.schedulerThreads = threads;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withExecutorThreads(final int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("Executor threads must be at least 1");
        }
        this.executorThreads = threads;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withThreadNamePrefix(final @NonNull String prefix) {
        this.threadNamePrefix = prefix;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withPrettyJson() {
        this.prettyPrintJson = true;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withSerializeNulls() {
        this.serializeNulls = true;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withGson(final @NonNull Gson gson) {
        this.gson = gson;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withCustomScheduler(final @NonNull ScheduledExecutorService scheduler) {
        this.customScheduler = scheduler;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withCustomExecutor(final @NonNull Executor executor) {
        this.customExecutor = executor;
        return this;
    }

    public @NonNull LocalSedisCacheBuilder withCustomJedisPool(final @NonNull JedisPool jedisPool) {
        this.customJedisPool = jedisPool;
        return this;
    }

    public @NonNull LocalSedisCache build() {
        final ScheduledExecutorService scheduler = customScheduler != null
                ? customScheduler
                : Executors.newScheduledThreadPool(schedulerThreads, r -> {
            final Thread thread = new Thread(r);
            thread.setName(threadNamePrefix + "-scheduler-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });

        final Executor executor = customExecutor != null
                ? customExecutor
                : Executors.newFixedThreadPool(executorThreads, r -> {
            final Thread thread = new Thread(r);
            thread.setName(threadNamePrefix + "-executor-" + thread.threadId());
            thread.setDaemon(true);
            return thread;
        });

        final JedisPool jedisPool = customJedisPool != null
                ? customJedisPool
                : createJedisPool();

        final Gson gsonInstance = gson != null
                ? gson
                : createGson();

        return new LocalSedisCache(scheduler, executor, jedisPool, gsonInstance);
    }

    private @NonNull JedisPool createJedisPool() {
        final JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxIdle);
        config.setMinIdle(minIdle);
        config.setTestOnBorrow(testOnBorrow);
        config.setTestOnReturn(testOnReturn);
        config.setTestWhileIdle(testWhileIdle);
        config.setTimeBetweenEvictionRuns(timeBetweenEvictionRuns);
        config.setNumTestsPerEvictionRun(numTestsPerEvictionRun);
        config.setMinEvictableIdleDuration(minEvictableIdleTime);
        config.setSoftMinEvictableIdleDuration(softMinEvictableIdleTime);
        config.setBlockWhenExhausted(blockWhenExhausted);
        config.setMaxWait(maxWaitTime);

        return new JedisPool(config, host, port, (int) timeout.toMillis(), password, database);
    }

    private @NonNull Gson createGson() {
        final GsonBuilder builder = new GsonBuilder();

        if (prettyPrintJson) {
            builder.setPrettyPrinting();
        }

        if (serializeNulls) {
            builder.serializeNulls();
        }

        return builder.create();
    }
}