package com.ifood.crawler.core.model;

import java.time.Duration;
import java.time.Instant;

public record ExecutionSummary(
    long totalUrls,
    long successCount,
    long errorCount,
    double successRate,
    Duration totalDuration,
    Instant startTime,
    Instant endTime
) {
    public static ExecutionSummary of(long total, long success, long error, Duration duration, Instant start, Instant end) {
        double rate = total == 0 ? 0.0 : (double) success / total * 100;
        return new ExecutionSummary(total, success, error, rate, duration, start, end);
    }
    
    public String toFormattedString() {
        return String.format("""
                ========== EXECUTION SUMMARY ==========
                Total URLs processed: %d
                Successes: %d
                Failures: %d
                Success rate: %.2f%%
                Total time: %d seconds
                Start: %s
                End: %s
                ========================================""",
                totalUrls, successCount, errorCount, successRate, totalDuration.getSeconds(), startTime, endTime);
    }
}