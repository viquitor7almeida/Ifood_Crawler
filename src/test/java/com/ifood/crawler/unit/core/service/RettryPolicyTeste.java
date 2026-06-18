package com.ifood.crawler.unit.core.service;

import com.ifood.crawler.core.service.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetryPolicyTest {

    @Test
    void shouldCalculateBackoffCorrectly() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofSeconds(1))
                .multiplier(2.0)
                .maxBackoff(Duration.ofSeconds(10))
                .build();

        Duration backoff1 = policy.getBackoffForAttempt(1);
        Duration backoff2 = policy.getBackoffForAttempt(2);
        Duration backoff3 = policy.getBackoffForAttempt(3);

        // 1s +/- jitter (até 100ms)
        assertThat(backoff1.toMillis()).isBetween(1000L, 1100L);
        
        // 2s +/- jitter
        assertThat(backoff2.toMillis()).isBetween(2000L, 2100L);
        
        // 4s +/- jitter (mas <= maxBackoff)
        assertThat(backoff3.toMillis()).isBetween(4000L, 4100L);
        assertThat(backoff3.toMillis()).isLessThanOrEqualTo(10000L);
    }

    @Test
    void shouldRespectMaxBackoff() {
        RetryPolicy policy = RetryPolicy.builder()
                .initialBackoff(Duration.ofSeconds(1))
                .multiplier(2.0)
                .maxBackoff(Duration.ofSeconds(5))
                .build();

        Duration backoff5 = policy.getBackoffForAttempt(5); // 16s teóricos, mas limitado a 5s
        
        assertThat(backoff5.toMillis()).isLessThanOrEqualTo(5000L);
        assertThat(backoff5.toMillis()).isGreaterThanOrEqualTo(5000L); // jitter pode adicionar
    }

    @Test
    void shouldRetryUntilMaxAttempts() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .build();

        assertThat(policy.shouldRetry(new RuntimeException("Timeout"), 1)).isTrue();
        assertThat(policy.shouldRetry(new RuntimeException("Timeout"), 2)).isTrue();
        assertThat(policy.shouldRetry(new RuntimeException("Timeout"), 3)).isFalse();
    }

    @Test
    void shouldNotRetryOnNonRetryableExceptions() {
        RetryPolicy policy = RetryPolicy.builder()
                .maxRetries(3)
                .retryableExceptionPredicate(e -> e instanceof TimeoutException)
                .build();

        // Retryable
        assertThat(policy.shouldRetry(new TimeoutException(), 1)).isTrue();
        
        // Non-retryable
        assertThat(policy.shouldRetry(new IllegalArgumentException("Invalid URL"), 1)).isFalse();
    }

    @Test
    void shouldThrowExceptionWhenInvalidParams() {
        assertThatThrownBy(() -> RetryPolicy.builder()
                .maxRetries(0)
                .build())
                .isInstanceOf(IllegalArgumentException.class);
        
        assertThatThrownBy(() -> RetryPolicy.builder()
                .initialBackoff(Duration.ofMillis(-1))
                .build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}