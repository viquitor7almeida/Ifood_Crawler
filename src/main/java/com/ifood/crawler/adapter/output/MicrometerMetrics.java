package main.java.com.ifood.crawler.adapter.output;

import com.ifood.crawler.core.port.output.MetricsPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.concurrent.atomic.AtomicLong;

public class MicrometerMetrics implements MetricsPort {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final AtomicLong successCounter = registry.gauge("crawler.success", new AtomicLong(0));
    private final AtomicLong errorCounter = registry.gauge("crawler.error", new AtomicLong(0));

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
        registry.timer("crawler.duration", "url", url).record(java.time.Duration.ofMillis(millis));
    }

    @Override
    public void recordRetry(String url, int attempt) {
        registry.counter("crawler.retry", "url", url, "attempt", String.valueOf(attempt)).increment();
    }

    @Override
    public String generateReport() {
        long success = getSuccessCount();
        long error = getErrorCount();
        long total = success + error;
        double rate = total == 0 ? 0 : (double) success / total * 100;
        return String.format("""
                ========== METRICS REPORT ==========
                Success: %d
                Error: %d
                Total: %d
                Success Rate: %.2f%%
                ======================================
                """, success, error, total, rate);
    }

    @Override
    public long getSuccessCount() {
        return (long) registry.find("crawler.success").gauge().value();
    }

    @Override
    public long getErrorCount() {
        return (long) registry.find("crawler.error").gauge().value();
    }
}