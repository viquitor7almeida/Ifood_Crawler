package com.ifood.crawler.adapter.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

public class AppConfig {

    private final Properties props = new Properties();

    private int parallelism = 5;
    private int maxRetries = 3;
    private long initialBackoffMs = 1000;
    private double backoffMultiplier = 2.0;
    private long jitterMs = 200;
    private long rateLimitDelayMs = 1000;
    private long retryMaxBackoffMs = 30000;
    private int checkpointInterval = 10;
    private Duration navigationTimeout = Duration.ofSeconds(60);
    private Duration selectorTimeout = Duration.ofSeconds(10);
    private Duration renderTimeout = Duration.ofSeconds(15);
    private Duration flaresolverrTimeout = Duration.ofSeconds(180);
    private int flaresolverrSessionTtl = 3600;
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private String flaresolverrUrl = "http://flaresolverr:8191/v1";
    private Path inputFile = Path.of("data/ifood_urls_padrao_item_1000.csv");
    private Path outputFile = Path.of("output/results.json");
    private Path checkpointDbPath = Path.of("checkpoint.db");
    private Path cookieStorePath = Path.of("cookies.json");

    public AppConfig() {
        loadProperties();
        overrideWithEnv();
    }

    private void loadProperties() {
        try (InputStream is = getClass().getResourceAsStream("/application.properties")) {
            if (is != null) {
                props.load(is);
                parallelism = Integer.parseInt(props.getProperty("crawler.parallelism", "5"));
                maxRetries = Integer.parseInt(props.getProperty("crawler.max-retries", "3"));
                initialBackoffMs = Long.parseLong(props.getProperty("crawler.retry-backoff-initial-ms", "1000"));
                backoffMultiplier = Double.parseDouble(props.getProperty("crawler.retry-backoff-multiplier", "2.0"));
                jitterMs = Long.parseLong(props.getProperty("crawler.retry-jitter-ms", "200"));
                retryMaxBackoffMs = Long.parseLong(props.getProperty("crawler.retry-max-backoff-ms", "30000"));
                rateLimitDelayMs = Long.parseLong(props.getProperty("crawler.rate-limit-delay-ms", "1000"));
                checkpointInterval = Integer.parseInt(props.getProperty("crawler.checkpoint-interval", "10"));
                navigationTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("crawler.navigation-timeout", "60")));
                renderTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("crawler.render-timeout", "15")));
                selectorTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("crawler.selector-timeout", "10")));
                flaresolverrTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("crawler.flaresolverr-timeout", "180")));
                flaresolverrSessionTtl = Integer.parseInt(props.getProperty("crawler.flaresolverr-session-ttl", "3600"));
                userAgent = props.getProperty("crawler.user-agent", userAgent);
                flaresolverrUrl = props.getProperty("crawler.flaresolverr-url", flaresolverrUrl);
                inputFile = Path.of(props.getProperty("crawler.input-file", "data/ifood_urls_padrao_item_1000.csv"));
                outputFile = Path.of(props.getProperty("crawler.output-file", "output/results.json"));
                checkpointDbPath = Path.of(props.getProperty("crawler.checkpoint-db-path", "checkpoint.db"));
                cookieStorePath = Path.of(props.getProperty("crawler.cookie-store-path", "cookies.json"));
            }
        } catch (IOException e) {
            System.err.println("Nao foi possivel carregar application.properties, usando defaults.");
        }
    }

    private void overrideWithEnv() {
        parallelism = Integer.parseInt(System.getenv().getOrDefault("CRAWLER_PARALLELISM", String.valueOf(parallelism)));
        maxRetries = Integer.parseInt(System.getenv().getOrDefault("CRAWLER_MAX_RETRIES", String.valueOf(maxRetries)));
        initialBackoffMs = Long.parseLong(System.getenv().getOrDefault("CRAWLER_INITIAL_BACKOFF_MS", String.valueOf(initialBackoffMs)));
        retryMaxBackoffMs = Long.parseLong(System.getenv().getOrDefault("CRAWLER_MAX_BACKOFF_MS", String.valueOf(retryMaxBackoffMs)));
        rateLimitDelayMs = Long.parseLong(System.getenv().getOrDefault("CRAWLER_RATE_LIMIT_DELAY_MS", String.valueOf(rateLimitDelayMs)));
        checkpointInterval = Integer.parseInt(System.getenv().getOrDefault("CRAWLER_CHECKPOINT_INTERVAL", String.valueOf(checkpointInterval)));
        flaresolverrUrl = System.getenv().getOrDefault("CRAWLER_FLARESOLVERR_URL", flaresolverrUrl);
        flaresolverrTimeout = Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("CRAWLER_FLARESOLVERR_TIMEOUT", String.valueOf(flaresolverrTimeout.toSeconds()))));
        flaresolverrSessionTtl = Integer.parseInt(System.getenv().getOrDefault("CRAWLER_FLARESOLVERR_SESSION_TTL", String.valueOf(flaresolverrSessionTtl)));
        navigationTimeout = Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("CRAWLER_NAVIGATION_TIMEOUT", String.valueOf(navigationTimeout.toSeconds()))));
        renderTimeout = Duration.ofSeconds(Long.parseLong(System.getenv().getOrDefault("CRAWLER_RENDER_TIMEOUT", String.valueOf(renderTimeout.toSeconds()))));
        String env;
        env = System.getenv("CRAWLER_INPUT_FILE");
        if (env != null) inputFile = Path.of(env);
        env = System.getenv("CRAWLER_OUTPUT_FILE");
        if (env != null) outputFile = Path.of(env);
        env = System.getenv("CRAWLER_CHECKPOINT_DB_PATH");
        if (env != null) checkpointDbPath = Path.of(env);
        env = System.getenv("CRAWLER_COOKIE_STORE_PATH");
        if (env != null) cookieStorePath = Path.of(env);
    }

    public int getParallelism() { return parallelism; }
    public int getMaxRetries() { return maxRetries; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getJitterMs() { return jitterMs; }
    public long getRateLimitDelayMs() { return rateLimitDelayMs; }
    public long getRetryMaxBackoffMs() { return retryMaxBackoffMs; }
    public int getCheckpointInterval() { return checkpointInterval; }
    public Duration getNavigationTimeout() { return navigationTimeout; }
    public Duration getSelectorTimeout() { return selectorTimeout; }
    public Duration getRenderTimeout() { return renderTimeout; }
    public Duration getFlaresolverrTimeout() { return flaresolverrTimeout; }
    public int getFlaresolverrSessionTtl() { return flaresolverrSessionTtl; }
    public String getUserAgent() { return userAgent; }
    public String getFlaresolverrUrl() { return flaresolverrUrl; }
    public Path getInputFile() { return inputFile; }
    public Path getOutputFile() { return outputFile; }
    public Path getCheckpointDbPath() { return checkpointDbPath; }
    public Path getCookieStorePath() { return cookieStorePath; }
}
