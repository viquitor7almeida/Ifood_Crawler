package main.java.com.ifood.crawler.adapter.output;

import com.ifood.crawler.core.port.output.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MicrometerMetrics implements MetricsPort {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    
    // Contadores com tags
    private final AtomicLong successCounter = registry.gauge("crawler.success", new AtomicLong(0));
    private final AtomicLong errorCounter = registry.gauge("crawler.error", new AtomicLong(0));
    private final AtomicLong retryCounter = registry.gauge("crawler.retry", new AtomicLong(0));
    
    // Timer para duração
    private final Timer durationTimer = Timer.builder("crawler.duration")
            .description("Tempo de processamento por URL")
            .publishPercentiles(0.5, 0.9, 0.95, 0.99)
            .publishPercentileHistogram(true)
            .register(registry);
    
    // Histórico de URLs processadas (para métricas temporárias)
    private final ConcurrentHashMap<String, Long> urlLatencyMap = new ConcurrentHashMap<>();

    @Override
    public void incrementSuccess() {
        successCounter.incrementAndGet();
    }

    @Override
    public void incrementError() {
        errorCounter.incrementAndGet();
    }

    @Override
    public void recordDuration(String url, long millis) {
        durationTimer.record(Duration.ofMillis(millis));
        urlLatencyMap.put(url, millis);
    }

    @Override
    public void recordRetry(String url, int attempt) {
        retryCounter.incrementAndGet();
        registry.counter("crawler.retry.attempt", Tags.of("attempt", String.valueOf(attempt))).increment();
    }

    @Override
    public String generateReport() {
        long success = getSuccessCount();
        long error = getErrorCount();
        long total = success + error;
        double rate = total == 0 ? 0 : (double) success / total * 100;
        
        // Estatísticas de tempo
        double p50 = durationTimer.takeSnapshot().percentileValues()[0].value();
        double p90 = durationTimer.takeSnapshot().percentileValues()[1].value();
        double p95 = durationTimer.takeSnapshot().percentileValues()[2].value();
        
        return String.format("""
                ========== METRICS REPORT ==========
                Success: %d
                Error: %d
                Total: %d
                Success Rate: %.2f%%
                Retries: %d
                --- Latency (ms) ---
                P50: %.0f
                P90: %.0f
                P95: %.0f
                ======================================
                """, success, error, total, rate, getRetryCount(), p50, p90, p95);
    }

    @Override
    public long getSuccessCount() {
        return (long) registry.find("crawler.success").gauge().value();
    }

    @Override
    public long getErrorCount() {
        return (long) registry.find("crawler.error").gauge().value();
    }

    public long getRetryCount() {
        return (long) registry.find("crawler.retry").gauge().value();
    }
}