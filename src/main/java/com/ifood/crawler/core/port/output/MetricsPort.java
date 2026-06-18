package com.ifood.crawler.core.port.output;

public interface MetricsPort {
    void incrementSuccess();
    void incrementError();
    void recordDuration(String url, long millis);
    void recordRetry(String url, int attempt);
    String generateReport();
    long getSuccessCount();
    long getErrorCount();
}