package de.leycm.sedis.local;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.JedisPool;

import java.util.Objects;

/**
 * Factory class for constructing {@link LocalRedisCache} instances with flexible configuration.
 * Provides a fluent API for configuring Redis connection parameters, authentication,
 * and serialization components while preventing constructor code duplication.
 *
 * <p>This creator pattern offers several advantages:
 * <ul>
 *   <li>Eliminates the need for multiple overloaded constructors</li>
 *   <li>Provides semantic configuration methods with clear intent</li>
 *   <li>Supports partial configuration with sensible defaults</li>
 *   <li>Enables immutable {@code LocalRedisCache} instances once created</li>
 *   <li>Centralizes validation and construction logic</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong> Instances of this class are not thread-safe.
 * The builder should be configured and used within a single thread context.
 * However, the created {@code LocalRedisCache} instances are thread-safe.
 *
 * <h2>Usage Examples</h2>
 *
 * <p><strong>Example 1: Default Configuration</strong>
 * <pre>{@code
 * LocalRedisCache cache = LocalRedisCacheCreator.defaultBuilder().create();
 * }</pre>
 *
 * <p><strong>Example 2: Custom Host and Port</strong>
 * <pre>{@code
 * LocalRedisCache cache = LocalRedisCacheCreator.forAddress("0.0.0.0", 9000).create();
 * }</pre>
 *
 * <p><strong>Example 3: With Authentication</strong>
 * <pre>{@code
 * LocalRedisCache cache = LocalRedisCacheCreator.defaultBuilder()
 *     .withHost("0.0.0.0")
 *     .withPort(9000)
 *     .withPassword("secure_password")
 *     .create();
 * }</pre>
 *
 * <p><strong>Example 4: Using Existing JedisPool</strong>
 * <pre>{@code
 * JedisPool sharedPool = new JedisPool(poolConfig, "redis-host", 6379);
 * LocalRedisCache cache = LocalRedisCacheCreator.withExistingPool(sharedPool).create();
 * }</pre>
 *
 * <p><strong>Example 5: Full Custom Configuration</strong>
 * <pre>{@code
 * Gson customGson = new GsonBuilder()
 *     .setPrettyPrinting()
 *     .create();
 *
 * LocalRedisCache cache = LocalRedisCacheCreator.forSecuredInstance("0.0.0.0", 9000, "pass")
 *     .withGson(customGson)
 *     .create();
 * }</pre>
 *
 * <p><strong>Example 6: Fluent Configuration Chain</strong>
 * <pre>{@code
 * LocalRedisCache cache = LocalRedisCacheCreator.defaultBuilder()
 *     .withHost("production.redis.example.com")
 *     .withPort(6380)
 *     .withPassword(System.getenv("REDIS_PASSWORD"))
 *     .withGson(new GsonBuilder().serializeNulls().create())
 *     .create();
 * }</pre>
 * @see LocalRedisCache
 * @since 1.0
 */
public class LocalRedisCacheCreator {

    /**
     * Redis server hostname or IP address.
     * Defaults to {@code "localhost"} if not explicitly configured.
     */
    private String redisHost = "localhost";

    /**
     * Redis server port number.
     * Defaults to {@code 6379} (standard Redis port) if not explicitly configured.
     */
    private int redisPort = 6379;

    /**
     * Optional Redis authentication password.
     * A {@code null} value indicates no authentication is required.
     */
    private String redisPassword = null;

    /**
     * Optional pre-configured Jedis connection pool.
     * When provided, host, port, and password configurations are ignored.
     */
    private JedisPool jedisPool = null;

    /**
     * Optional custom Gson instance for JSON serialization.
     * If {@code null}, a default Gson instance will be created.
     */
    private Gson gson = null;

    /**
     * Private constructor to enforce use of factory methods.
     * Prevents direct instantiation and ensures consistent initialization.
     */
    private LocalRedisCacheCreator() { }

    /**
     * Configures the Redis server host address.
     *
     * @param host Redis server hostname or IP address (must not be {@code null} or empty)
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if {@code host} is {@code null} or empty
     */
    public LocalRedisCacheCreator withHost(final @NonNull String host) {
        if (host.trim().isEmpty()) {
            throw new IllegalArgumentException("Redis host cannot be empty");
        }
        this.redisHost = host;
        return this;
    }

    /**
     * Configures the Redis server port.
     *
     * @param port Redis server port number (must be between 1 and 65535 inclusive)
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if {@code port} is outside valid range
     */
    public LocalRedisCacheCreator withPort(final int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(
                    String.format("Port must be between 1 and 65535, got: %d", port)
            );
        }
        this.redisPort = port;
        return this;
    }

    /**
     * Configures Redis authentication password.
     * <p>
     * Use {@code null} to explicitly disable authentication.
     * An empty string password is not recommended but permitted.
     *
     * @param password Redis authentication password, or {@code null} for no authentication
     * @return this builder instance for method chaining
     */
    public LocalRedisCacheCreator withPassword(final @NonNull String password) {
        this.redisPassword = password;
        return this;
    }

    /**
     * Configures a pre-existing Jedis connection pool.
     * <p>
     * When this method is invoked, any previously configured host, port, or password
     * settings are disregarded. The provided pool must be properly initialized and
     * thread-safe.
     *
     * @param pool Pre-configured Jedis connection pool (must not be {@code null})
     * @return this builder instance for method chaining
     * @throws NullPointerException if {@code pool} is {@code null}
     */
    public LocalRedisCacheCreator withJedisPool(final @NonNull JedisPool pool) {
        this.jedisPool = Objects.requireNonNull(pool, "JedisPool cannot be null");
        return this;
    }

    /**
     * Configures a custom Gson instance for JSON serialization.
     * <p>
     * Use this method when custom serialization policies are required.
     * The provided instance will be used for all JSON operations within
     * the created {@code LocalRedisCache}.
     *
     * @param gson Custom Gson instance (must not be {@code null})
     * @return this builder instance for method chaining
     * @throws NullPointerException if {@code gson} is {@code null}
     */
    public LocalRedisCacheCreator withGson(final @NonNull Gson gson) {
        this.gson = Objects.requireNonNull(gson, "Gson instance cannot be null");
        return this;
    }

    /**
     * Constructs a {@link LocalRedisCache} instance based on the current configuration.
     * <p>
     * This method selects the appropriate constructor based on the configured parameters:
     * <ol>
     *   <li>If a JedisPool is configured, delegates to {@link LocalRedisCache#LocalRedisCache(JedisPool, Gson)}</li>
     *   <li>If a password is configured, delegates to {@link LocalRedisCache#LocalRedisCache(String, int, String)}</li>
     *   <li>Otherwise, delegates to {@link LocalRedisCache#LocalRedisCache(String, int)}</li>
     * </ol>
     *
     * @return A fully configured {@code LocalRedisCache} instance
     * @throws IllegalStateException if both JedisPool and host/port are partially configured
     */
    public LocalRedisCache create() {
        validateConfiguration();

        if (jedisPool != null) {
            // Utilize existing JedisPool with optional custom Gson
            return new LocalRedisCache(jedisPool, gson != null ? gson : createDefaultGson());
        } else if (redisPassword != null) {
            // Use host, port, and authentication
            return new LocalRedisCache(redisHost, redisPort, redisPassword);
        } else {
            // Use host and port only (no authentication)
            return new LocalRedisCache(redisHost, redisPort);
        }
    }

    /**
     * Validates the current configuration state.
     * Ensures that incompatible configurations are detected early.
     *
     * @throws IllegalStateException if configuration is inconsistent
     */
    private void validateConfiguration() {
        if (jedisPool != null) return; // When using existing pool, host/port/password are ignored

        Objects.requireNonNull(redisHost, "Redis host must be configured");
        if (redisHost.trim().isEmpty()) {
            throw new IllegalStateException("Redis host cannot be empty");
        }
    }

    /**
     * Creates a default Gson instance with standard configuration.
     * This implementation serializes null values and uses default date formatting.
     *
     * @return A pre-configured Gson instance
     */
    @Contract(" -> new")
    private @NotNull Gson createDefaultGson() {
        return new GsonBuilder()
                .serializeNulls()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .disableHtmlEscaping()
                .create();
    }

    /**
     * Creates a builder instance with default configuration.
     * <p>
     * Default configuration:
     * <ul>
     *   <li>Host: {@code "localhost"}</li>
     *   <li>Port: {@code 6379}</li>
     *   <li>No authentication</li>
     *   <li>No pre-configured JedisPool</li>
     *   <li>Default Gson serialization</li>
     * </ul>
     *
     * @return A builder instance with default settings
     */
    @Contract(value = " -> new", pure = true)
    public static @NotNull LocalRedisCacheCreator defaultBuilder() {
        return new LocalRedisCacheCreator();
    }

    /**
     * Creates a builder pre-configured with specific host and port.
     * <p>
     * This factory method is semantically equivalent to calling:
     * <pre>{@code
     * LocalRedisCacheCreator.defaultBuilder()
     *     .withHost(host)
     *     .withPort(port);
     * }</pre>
     *
     * @param host Redis server hostname or IP address
     * @param port Redis server port number
     * @return A builder pre-configured with the specified address
     * @throws IllegalArgumentException if {@code host} is null/empty or port is invalid
     */
    public static LocalRedisCacheCreator forAddress(final @NonNull String host, final int port) {
        return new LocalRedisCacheCreator()
                .withHost(host)
                .withPort(port);
    }

    /**
     * Creates a builder configured to use an existing JedisPool.
     * <p>
     * This method is intended for scenarios where connection pool management
     * is externalized or shared across multiple components.
     *
     * @param pool Pre-configured Jedis connection pool
     * @return A builder configured to use the specified pool
     * @throws NullPointerException if {@code pool} is {@code null}
     */
    public static LocalRedisCacheCreator withExistingPool(final @NonNull JedisPool pool) {
        return new LocalRedisCacheCreator()
                .withJedisPool(pool);
    }

    /**
     * Creates a builder for a secured Redis instance with authentication.
     *
     * @param host Redis server hostname or IP address
     * @param port Redis server port number
     * @param password Authentication password (may, be {@code null})
     * @return A builder configured for authenticated Redis access
     * @throws IllegalArgumentException if {@code host} is null/empty or port is invalid
     */
    public static LocalRedisCacheCreator forSecuredInstance(final @NonNull String host,
                                                            final int port,
                                                            final @NonNull String password) {
        return new LocalRedisCacheCreator()
                .withHost(host)
                .withPort(port)
                .withPassword(password);
    }

}