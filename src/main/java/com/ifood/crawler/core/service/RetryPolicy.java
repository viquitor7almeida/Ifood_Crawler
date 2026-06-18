package main.java.com.ifood.crawler.core.service;

import java.time.Duration;
import java.util.function.Predicate;

/**
 *política de retry com exponential backoff e jitter.
 */
public class RetryPolicy {

    private final int maxRetries;
    private final Duration initialBackoff;
    private final double multiplier;
    private final Duration maxBackoff;
    private final Duration timeoutPerAttempt;
    private final Predicate<Exception> retryableExceptionPredicate;

    private RetryPolicy(Builder builder) {
        this.maxRetries = builder.maxRetries;
        this.initialBackoff = builder.initialBackoff;
        this.multiplier = builder.multiplier;
        this.maxBackoff = builder.maxBackoff;
        this.timeoutPerAttempt = builder.timeoutPerAttempt;
        this.retryableExceptionPredicate = builder.retryableExceptionPredicate;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public Duration getBackoffForAttempt(int attempt) {
        if (attempt <= 1) return initialBackoff;
        long backoffMillis = (long) (initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1));
        // Aplica jitter (0-100ms)
        long jitter = (long) (Math.random() * 100);
        long finalBackoff = Math.min(backoffMillis + jitter, maxBackoff.toMillis());
        return Duration.ofMillis(finalBackoff);
    }

    public Duration getTimeoutPerAttempt() {
        return timeoutPerAttempt;
    }

    public boolean shouldRetry(Exception e, int attempt) {
        return attempt < maxRetries && retryableExceptionPredicate.test(e);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxRetries = 3;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private double multiplier = 2.0;
        private Duration maxBackoff = Duration.ofSeconds(30);
        private Duration timeoutPerAttempt = Duration.ofSeconds(30);
        private Predicate<Exception> retryableExceptionPredicate = e -> true;

        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }

        public Builder initialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
            return this;
        }

        public Builder timeoutPerAttempt(Duration timeoutPerAttempt) {
            this.timeoutPerAttempt = timeoutPerAttempt;
            return this;
        }

        public Builder retryableExceptionPredicate(Predicate<Exception> predicate) {
            this.retryableExceptionPredicate = predicate;
            return this;
        }

        public RetryPolicy build() {
            return new RetryPolicy(this);
        }
    }
}