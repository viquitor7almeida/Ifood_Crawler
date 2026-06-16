package com.ifood.crawler;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.adapter.input.CsvUrlProvider;
import com.ifood.crawler.adapter.output.*;
import com.ifood.crawler.core.model.ExecutionSummary;
import com.ifood.crawler.core.port.input.CrawlerOrchestrator;
import com.ifood.crawler.core.port.input.UrlProvider;
import com.ifood.crawler.core.port.output.*;
import com.ifood.crawler.core.service.CrawlerOrchestratorImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IfoodCrawlerApplication {

    private static final Logger log = LoggerFactory.getLogger(IfoodCrawlerApplication.class);

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        log.info("Carregando configurações: paralelismo={}, maxRetries={}, rateLimitDelay={}ms",
                config.getParallelism(), config.getMaxRetries(), config.getRateLimitDelayMs());

        UrlProvider urlProvider = new CsvUrlProvider(config.getInputFile());
        CrawlerPort crawlerPort = new PlaywrightCrawler(config);
        ParserPort parserPort = new ResilientParser();
        PersistencePort persistencePort = new SqlitePersistence(config);
        MetricsPort metricsPort = new MicrometerMetrics();

        CrawlerOrchestrator orchestrator = new CrawlerOrchestratorImpl(
                urlProvider, crawlerPort, parserPort, persistencePort, metricsPort,
                config.getParallelism(), config.getMaxRetries(),
                config.getInitialBackoffMs(), config.getBackoffMultiplier(),
                config.getJitterMs(), config.getRateLimitDelayMs(),
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