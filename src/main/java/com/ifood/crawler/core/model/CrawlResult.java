package com.ifood.crawler.core.model;

import java.time.Instant;
import java.util.Objects;

public record CrawlResult(
    String url,
    ProductData productData,
    int attemptCount,
    long durationMillis,
    Instant timestamp,
    boolean isFromRetry
) {
    
    public CrawlResult {
        Objects.requireNonNull(url, "url não pode ser nulo");
        Objects.requireNonNull(productData, "productData não pode ser nulo");
        if (attemptCount < 1) throw new IllegalArgumentException("attemptCount deve ser >= 1");
        if (durationMillis < 0) throw new IllegalArgumentException("durationMillis não pode ser negativo");
        if (timestamp == null) timestamp = Instant.now();
    }
    
    public static CrawlResult fromSuccess(String url, ProductData data, int attemptCount, long duration, boolean isFromRetry) {
        return new CrawlResult(url, data, attemptCount, duration, Instant.now(), isFromRetry);
    }
    
    public static CrawlResult fromError(String url, ProductData errorData, int attemptCount, long duration, boolean isFromRetry) {
        return new CrawlResult(url, errorData, attemptCount, duration, Instant.now(), isFromRetry);
    }
}