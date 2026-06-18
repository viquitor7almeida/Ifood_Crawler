package com.ifood.crawler;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.adapter.input.CsvUrlProvider;
import com.ifood.crawler.adapter.output.*;
import com.ifood.crawler.core.model.ExecutionSummary;
import com.ifood.crawler.core.port.input.CrawlerOrchestrator;
import com.ifood.crawler.core.port.input.UrlProvider;
import com.ifood.crawler.core.port.output.*;
import com.ifood.crawler.core.service.CrawlerOrchestratorImpl;
import com.ifood.crawler.core.service.RetryPolicy;
import com.ifood.crawler.core.service.TokenBucketRateLimiter;
import com.ifood.crawler.infra.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

public class IfoodCrawlerApplication {

    private static final Logger log = LoggerFactory.getLogger(IfoodCrawlerApplication.class);

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        log.info("Carregando configurações: paralelismo={}, maxRetries={}",
                config.getParallelism(), config.getMaxRetries());

        UrlProvider urlProvider = new CsvUrlProvider(config.getInputFile());
        CrawlerPort crawlerPort = new PlaywrightCrawler(config);
        ParserPort parserPort = new ResilientParser();
        PersistencePort persistencePort = new SqlitePersistence(config);
        MetricsPort metricsPort = new MicrometerMetrics();

        HealthCheck healthCheck = new HealthCheck(metricsPort);
        
        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(config.getMaxRetries())
                .initialBackoff(Duration.ofMillis(config.getInitialBackoffMs()))
                .multiplier(config.getBackoffMultiplier())
                .maxBackoff(Duration.ofMillis(30000))
                .build();
        
        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(
                config.getParallelism() * 2,
                Duration.ofSeconds(1)
        );

        CrawlerOrchestrator orchestrator = new CrawlerOrchestratorImpl(
                urlProvider,
                crawlerPort,
                parserPort,
                persistencePort,
                metricsPort,
                healthCheck,
                retryPolicy,
                rateLimiter,
                config.getParallelism(),
                config.getCheckpointInterval()
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Encerrando crawler...");
            crawlerPort.close();
        }));

        ExecutionSummary summary = orchestrator.run();
        System.out.println(summary.toFormattedString());
        log.info(metricsPort.generateReport());
        crawlerPort.close();
    }
}