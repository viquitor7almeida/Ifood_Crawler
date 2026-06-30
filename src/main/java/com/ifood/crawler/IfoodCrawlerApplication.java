package com.ifood.crawler;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.adapter.input.CsvUrlProvider;
import com.ifood.crawler.adapter.output.*;
import com.ifood.crawler.core.model.ExecutionSummary;
import com.ifood.crawler.core.model.FetchedPage;
import com.ifood.crawler.core.port.input.CrawlerOrchestrator;
import com.ifood.crawler.core.port.input.UrlProvider;
import com.ifood.crawler.core.port.output.*;
import com.ifood.crawler.core.service.CrawlerOrchestratorImpl;
import com.ifood.crawler.core.service.RetryPolicy;
import com.ifood.crawler.core.service.TokenBucketRateLimiter;
import com.ifood.crawler.infra.CookieStore;
import com.ifood.crawler.infra.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IfoodCrawlerApplication {

    private static final Logger log = LoggerFactory.getLogger(IfoodCrawlerApplication.class);

    public static void main(String[] args) {
        AppConfig config = new AppConfig();
        log.info("Carregando configuracoes: paralelismo={}, maxRetries={}",
                config.getParallelism(), config.getMaxRetries());

        CookieStore cookieStore = new CookieStore(config.getCookieStorePath());
        log.info("CookieStore carregado: {} cookies salvos de {}", cookieStore.getCookies().size(), config.getCookieStorePath());

        UrlProvider urlProvider = new CsvUrlProvider(config.getInputFile());

        FlaresolverrCrawler flaresolverr = new FlaresolverrCrawler(
                config.getFlaresolverrUrl(), config.getFlaresolverrTimeout(), cookieStore);
        SimpleHttpCrawler simpleHttp = new SimpleHttpCrawler(cookieStore, config.getNavigationTimeout());

        List<CrawlerPort> crawlers = new ArrayList<>();
        crawlers.add(flaresolverr);
        crawlers.add(simpleHttp);

        ParserPort parserPort = new ResilientParser();
        PersistencePort persistencePort = new SqlitePersistence(config);
        MetricsPort metricsPort = new MicrometerMetrics();

        HealthCheck healthCheck = new HealthCheck(metricsPort);

        RetryPolicy retryPolicy = RetryPolicy.builder()
                .maxRetries(config.getMaxRetries())
                .initialBackoff(Duration.ofMillis(500))
                .multiplier(config.getBackoffMultiplier())
                .maxBackoff(Duration.ofMillis(15000))
                .build();

        TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(
                config.getParallelism() * 3,
                Duration.ofMillis(500)
        );

        CrawlerOrchestrator orchestrator = new CrawlerOrchestratorImpl(
                urlProvider,
                crawlers,
                parserPort,
                persistencePort,
                metricsPort,
                healthCheck,
                retryPolicy,
                rateLimiter,
                config.getParallelism(),
                config.getCheckpointInterval()
        );

        flaresolverr.prime();

        if (cookieStore.getCookies().isEmpty()) {
            log.info("CookieStore vazio — warmup via Flaresolverr...");
            Optional<String> firstUrl = urlProvider.urls().findFirst();
            if (firstUrl.isPresent()) {
                for (int i = 1; i <= 3; i++) {
                    log.info("Warmup tentativa {}/3: {}", i, firstUrl.get());
                    Optional<FetchedPage> result = flaresolverr.fetchPage(firstUrl.get());
                    if (result.isPresent() && result.get().isSuccess()) {
                        log.info("Warmup OK na tentativa {}! Cookies: {}", i, cookieStore.getCookies().size());
                        break;
                    }
                    if (!cookieStore.getCookies().isEmpty()) {
                        log.info("Warmup: cookies obtidos na tentativa {}", i);
                        break;
                    }
                    log.warn("Warmup tentativa {} falhou, aguardando 5s...", i);
                    if (i < 3) {
                        try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); break; }
                    }
                }
            }
        } else {
            log.info("CookieStore ja possui {} cookies, pulando warmup", cookieStore.getCookies().size());
        }

        ExecutionSummary summary = orchestrator.run();
        System.out.println(summary.toFormattedString());
        log.info(metricsPort.generateReport());
    }
}
