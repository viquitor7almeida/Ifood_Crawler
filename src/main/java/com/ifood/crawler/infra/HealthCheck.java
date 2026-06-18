package main.java.com.ifood.crawler.infra;

import com.ifood.crawler.core.port.output.MetricsPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

/**
 * Gera um arquivo de health check para monitoramento externo.
 */
public class HealthCheck {

    private final MetricsPort metricsPort;
    private final Path healthFile = Path.of("crawler.health");

    public HealthCheck(MetricsPort metricsPort) {
        this.metricsPort = metricsPort;
    }

    public void update() {
        try {
            String content = String.format("""
                    timestamp: %s
                    success: %d
                    error: %d
                    total: %d
                    success_rate: %.2f%%
                    """,
                    Instant.now(),
                    metricsPort.getSuccessCount(),
                    metricsPort.getErrorCount(),
                    metricsPort.getSuccessCount() + metricsPort.getErrorCount(),
                    (metricsPort.getSuccessCount() + metricsPort.getErrorCount()) > 0 ?
                            (double) metricsPort.getSuccessCount() /
                            (metricsPort.getSuccessCount() + metricsPort.getErrorCount()) * 100 : 0
            );
            Files.writeString(healthFile, content);
        } catch (IOException e) {
            // Silencioso
        }
    }
}