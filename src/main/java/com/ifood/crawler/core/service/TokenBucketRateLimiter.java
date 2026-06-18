package com.ifood.crawler.core.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 *rate limiter baseado em Token Bucket.
 */
public class TokenBucketRateLimiter {

    private final long maxTokens;
    private final Duration refillInterval;
    private final AtomicLong tokens;
    private volatile Instant lastRefillTime;

    public TokenBucketRateLimiter(long maxTokens, Duration refillInterval) {
        this.maxTokens = maxTokens;
        this.refillInterval = refillInterval;
        this.tokens = new AtomicLong(maxTokens);
        this.lastRefillTime = Instant.now();
    }

    /**
     *tenta adquirir um token. bloqueia ate que haja token disponível.
     * @param timeout timeout maximo para esperar
     * @return true se adquiriu, false se timeout
     */
    public boolean tryAcquire(Duration timeout) throws InterruptedException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout.toMillis()) {
            refillTokens();
            long currentTokens = tokens.get();
            if (currentTokens > 0 && tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return true;
            }
            Thread.sleep(10); //espera curta antes de tentar novamente
        }
        return false;
    }

    /**
     *adquire um token bloqueando indefinidamente.
     */
    public void acquire() throws InterruptedException {
        while (true) {
            refillTokens();
            long currentTokens = tokens.get();
            if (currentTokens > 0 && tokens.compareAndSet(currentTokens, currentTokens - 1)) {
                return;
            }
            Thread.sleep(10);
        }
    }

    private void refillTokens() {
        Instant now = Instant.now();
        Duration elapsed = Duration.between(lastRefillTime, now);
        if (elapsed.compareTo(refillInterval) >= 0) {
            synchronized (this) {
                // Double-check após adquirir lock
                elapsed = Duration.between(lastRefillTime, Instant.now());
                if (elapsed.compareTo(refillInterval) >= 0) {
                    long tokensToAdd = elapsed.toMillis() / refillInterval.toMillis();
                    long newTokens = Math.min(maxTokens, tokens.get() + tokensToAdd);
                    tokens.set(newTokens);
                    lastRefillTime = now;
                }
            }
        }
    }

    public long getAvailableTokens() {
        refillTokens();
        return tokens.get();
    }
}