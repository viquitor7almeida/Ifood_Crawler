package main.java.com.ifood.crawler.adapter.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 *carrega configurações de application.properties e variaveis de ambiente.
 *variaveis de ambiente sobrescrevem as properties.
 */
public class AppConfig {

    private final Properties props = new Properties();

    //configuraçoes com valores padrao
    private int parallelism = 5;
    private int maxRetries = 3;
    private long initialBackoffMs = 1000;
    private double backoffMultiplier = 2.0;
    private long jitterMs = 200;
    private long rateLimitDelayMs = 1000;
    private int checkpointInterval = 10;
    private Duration navigationTimeout = Duration.ofSeconds(30);
    private Duration selectorTimeout = Duration.ofSeconds(10);
    private String userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";
    private Path inputFile = Path.of("urls/ifood_urls.csv");
    private Path outputFile = Path.of("output/results.json");
    private Path checkpointDbPath = Path.of("checkpoint.db");

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
                rateLimitDelayMs = Long.parseLong(props.getProperty("crawler.rate-limit-delay-ms", "1000"));
                checkpointInterval = Integer.parseInt(props.getProperty("crawler.checkpoint-interval", "10"));
                navigationTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("crawler.navigation-timeout", "30")));
                selectorTimeout = Duration.ofSeconds(Long.parseLong(props.getProperty("crawler.selector-timeout", "10")));
                userAgent = props.getProperty("crawler.user-agent", userAgent);
                inputFile = Path.of(props.getProperty("crawler.input-file", "urls/ifood_urls.csv"));
                outputFile = Path.of(props.getProperty("crawler.output-file", "output/results.json"));
                checkpointDbPath = Path.of(props.getProperty("crawler.checkpoint-db-path", "checkpoint.db"));
            }
        } catch (IOException e) {
            System.err.println("Não foi possível carregar application.properties, usando defaults.");
        }
    }

    private void overrideWithEnv() {
        parallelism = Integer.parseInt(System.getenv().getOrDefault("CRAWLER_PARALLELISM", String.valueOf(parallelism)));
        maxRetries = Integer.parseInt(System.getenv().getOrDefault("CRAWLER_MAX_RETRIES", String.valueOf(maxRetries)));
        initialBackoffMs = Long.parseLong(System.getenv().getOrDefault("CRAWLER_INITIAL_BACKOFF_MS", String.valueOf(initialBackoffMs)));
        rateLimitDelayMs = Long.parseLong(System.getenv().getOrDefault("CRAWLER_RATE_LIMIT_DELAY_MS", String.valueOf(rateLimitDelayMs)));
    }

    //getters
    public int getParallelism() { return parallelism; }
    public int getMaxRetries() { return maxRetries; }
    public long getInitialBackoffMs() { return initialBackoffMs; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getJitterMs() { return jitterMs; }
    public long getRateLimitDelayMs() { return rateLimitDelayMs; }
    public int getCheckpointInterval() { return checkpointInterval; }
    public Duration getNavigationTimeout() { return navigationTimeout; }
    public Duration getSelectorTimeout() { return selectorTimeout; }
    public String getUserAgent() { return userAgent; }
    public Path getInputFile() { return inputFile; }
    public Path getOutputFile() { return outputFile; }
    public Path getCheckpointDbPath() { return checkpointDbPath; }
}