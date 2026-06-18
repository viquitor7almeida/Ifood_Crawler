package com.ifood.crawler.unit.core.service;

import com.ifood.crawler.core.service.TokenBucketRateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBucketRateLimiterTest {

    @Test
    void shouldLimitRequestsToMaxTokens() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, Duration.ofSeconds(1));
        ExecutorService executor = Executors.newFixedThreadPool(10);
        AtomicInteger success = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(10);

        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    // Tenta adquirir com timeout curto (100ms)
                    if (limiter.tryAcquire(Duration.ofMillis(100))) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(1, TimeUnit.SECONDS);
        // No máximo 5 tokens disponíveis imediatamente
        assertThat(success.get()).isLessThanOrEqualTo(5);
        executor.shutdown();
    }

    @Test
    void shouldRefillTokensOverTime() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, Duration.ofMillis(500));
        
        // Consome os 2 tokens iniciais
        assertThat(limiter.tryAcquire(Duration.ofMillis(100))).isTrue();
        assertThat(limiter.tryAcquire(Duration.ofMillis(100))).isTrue();
        assertThat(limiter.tryAcquire(Duration.ofMillis(100))).isFalse(); // Sem tokens
        
        // Aguarda refill
        Thread.sleep(600);
        assertThat(limiter.tryAcquire(Duration.ofMillis(100))).isTrue(); // Token refill
    }

    @Test
    void shouldGetAvailableTokensCorrectly() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(10, Duration.ofSeconds(1));
        
        assertThat(limiter.getAvailableTokens()).isEqualTo(10);
        
        // Consome 3 tokens
        for (int i = 0; i < 3; i++) {
            try {
                limiter.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        assertThat(limiter.getAvailableTokens()).isEqualTo(7);
    }

    @Test
    void shouldAcquireBlockingWhenNoTokens() throws InterruptedException {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, Duration.ofSeconds(1));
        
        // Consome o único token
        limiter.acquire();
        
        // Inicia thread que tenta adquirir (deve bloquear)
        AtomicInteger acquired = new AtomicInteger(0);
        Thread t = new Thread(() -> {
            try {
                limiter.acquire();
                acquired.set(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.start();
        
        // Aguarda 100ms para ver se bloqueou
        Thread.sleep(100);
        assertThat(acquired.get()).isZero(); // Ainda bloqueado
        
        // Aguarda refill
        Thread.sleep(1200);
        assertThat(acquired.get()).isOne(); // Deve ter adquirido
        t.join(1000);
    }
}