package com.ifood.crawler.infra;

import com.ifood.crawler.core.port.output.MetricsPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

public class HealthCheck {

    private final MetricsPort metricsPort;
    private final Path healthFile = Path.of("checkpoints/crawler.health");

    public HealthCheck(MetricsPort metricsPort) {
        this.metricsPort = metricsPort;
    }

    public void update() {
        try {
            Files.createDirectories(healthFile.getParent());
            long success = metricsPort.getSuccessCount();
            long error = metricsPort.getErrorCount();
            long total = success + error;
            double rate = total == 0 ? 0 : (double) success / total * 100;
            
            String content = String.format("""
                    timestamp: %s
                    success: %d
                    error: %d
                    total: %d
                    success_rate: %.2f%%
                    """,
                    Instant.now(),
                    success,
                    error,
                    total,
                    rate
            );
            Files.writeString(healthFile, content);
        } catch (IOException e) {
            // Silencioso
        }
    }
}