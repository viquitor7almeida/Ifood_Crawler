package com.ifood.crawler.core.service;

import com.ifood.crawler.core.model.CrawlResult;
import com.ifood.crawler.core.model.ExecutionSummary;
import com.ifood.crawler.core.model.FetchedPage;
import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.input.CrawlerOrchestrator;
import com.ifood.crawler.core.port.input.UrlProvider;
import com.ifood.crawler.core.port.output.*;
import com.ifood.crawler.infra.HealthCheck;
import com.ifood.crawler.infra.logging.StructuredLogger;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static java.util.concurrent.TimeUnit.SECONDS;

public class CrawlerOrchestratorImpl implements CrawlerOrchestrator {

    private final StructuredLogger log = StructuredLogger.getLogger(CrawlerOrchestratorImpl.class);

    private final UrlProvider urlProvider;
    private final List<CrawlerPort> crawlerPorts;
    private final ParserPort parserPort;
    private final PersistencePort persistencePort;
    private final MetricsPort metricsPort;
    private final HealthCheck healthCheck;
    private final RetryPolicy retryPolicy;
    private final TokenBucketRateLimiter rateLimiter;
    private final int parallelism;
    private final int checkpointInterval;

    private final ExecutorService workerPool;
    private final AtomicInteger processedSinceLastCheckpoint = new AtomicInteger(0);
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private volatile boolean shutdownRequested = false;

    public CrawlerOrchestratorImpl(UrlProvider urlProvider,
                                   List<CrawlerPort> crawlerPorts,
                                   ParserPort parserPort,
                                   PersistencePort persistencePort,
                                   MetricsPort metricsPort,
                                   HealthCheck healthCheck,
                                   RetryPolicy retryPolicy,
                                   TokenBucketRateLimiter rateLimiter,
                                   int parallelism,
                                   int checkpointInterval) {
        this.urlProvider = urlProvider;
        this.crawlerPorts = crawlerPorts;
        this.parserPort = parserPort;
        this.persistencePort = persistencePort;
        this.metricsPort = metricsPort;
        this.healthCheck = healthCheck;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.parallelism = parallelism;
        this.checkpointInterval = checkpointInterval;
        this.workerPool = Executors.newFixedThreadPool(parallelism);

        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Graceful shutdown iniciado...");
            shutdownRequested = true;

            if (!workerPool.isShutdown()) {
                workerPool.shutdown();
                try {
                    if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("Forcando shutdown de workers apos timeout");
                        workerPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    workerPool.shutdownNow();
                }
            }

            try {
                persistencePort.exportFinalResults(persistencePort.getAllResults());
            } catch (Exception e) {
                log.error("Falha ao salvar checkpoint final", e);
            }

            closeCrawlers();
            log.info("Graceful shutdown concluido.");
        }));
    }

    private void closeCrawlers() {
        for (CrawlerPort crawler : crawlerPorts) {
            try {
                crawler.close();
            } catch (Exception e) {
                log.warn("Erro ao fechar crawler {}: {}", crawler.name(), e.getMessage());
            }
        }
    }

    @Override
    public ExecutionSummary run() {
        Instant start = Instant.now();
        long totalUrls = urlProvider.totalUrls();
        log.info("Iniciando crawler com {} URLs, paralelismo {}, maxRetries {}, crawlers: {}",
                totalUrls, parallelism, retryPolicy.getMaxRetries(),
                crawlerPorts.stream().map(CrawlerPort::name).toList());

        ScheduledExecutorService progressTimer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "progress-timer");
            t.setDaemon(true);
            return t;
        });
        progressTimer.scheduleAtFixedRate(() -> {
            long processed = totalProcessed.get();
            long succ = metricsPort.getSuccessCount();
            long err = metricsPort.getErrorCount();
            long remaining = totalUrls - processed;
            double rate = (succ + err) > 0 ? (double) succ / (succ + err) * 100 : 0;
            log.info("PROGRESSO: {}/{} URLs, {}/{} sucesso/erro ({}%), restantes {}",
                    processed, totalUrls, succ, err, String.format("%.1f", rate), remaining);
        }, 15, 15, SECONDS);

        try (Stream<String> urlStream = urlProvider.urls()) {
            urlStream.forEach(url -> {
                if (shutdownRequested) return;

                if (persistencePort.isAlreadyProcessed(url)) {
                    log.debug("URL ja processada com sucesso, ignorando: {}", url);
                    totalProcessed.incrementAndGet();
                    return;
                }
                workerPool.submit(() -> processWithRetry(url));
            });
        }

        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(30, TimeUnit.MINUTES)) {
                log.warn("Timeout aguardando workers, forcando shutdown...");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }

        Instant end = Instant.now();
        Duration totalDuration = Duration.between(start, end);

        long processed = totalProcessed.get();
        long succ = metricsPort.getSuccessCount();
        long err = metricsPort.getErrorCount();

        ExecutionSummary summary = ExecutionSummary.of(
                processed, succ, err, totalDuration, start, end
        );

        progressTimer.shutdownNow();

        try {
            persistencePort.exportFinalResults(persistencePort.getAllResults());
            log.info("Resultados finais exportados com sucesso");
        } catch (Exception e) {
            log.error("Falha ao exportar resultados finais", e);
        }

        log.info(summary.toFormattedString());
        log.info(metricsPort.generateReport());
        healthCheck.update();

        return summary;
    }

    private void processWithRetry(String urlString) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        StructuredLogger taskLog = StructuredLogger.getLogger(CrawlerOrchestratorImpl.class, correlationId);

        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        URL urlObj;

        try {
            urlObj = new URL(urlString);
        } catch (Exception e) {
            taskLog.error("URL invalida: {}", e.getMessage());
            metricsPort.incrementError();
            totalProcessed.incrementAndGet();
            persistencePort.saveResult(CrawlResult.fromError(urlString,
                    ProductData.error(null, "URL invalida: " + e.getMessage()), 1, 0, false));
            healthCheck.update();
            return;
        }

        taskLog.infoWithContext("Iniciando processamento da URL", Map.of(
                "url", urlString,
                "maxRetries", retryPolicy.getMaxRetries()
        ));

        for (int attempt = 1; attempt <= retryPolicy.getMaxRetries(); attempt++) {
            if (shutdownRequested) {
                taskLog.warn("Shutdown solicitado, abortando: {}", urlString);
                return;
            }

            try {
                rateLimiter.acquire();
                FetchedPage page = fetchWithFallback(urlString, taskLog);

                if (page != null && page.isSuccess()) {
                    ProductData data = parserPort.parse(page.html(), urlObj);
                    long duration = System.currentTimeMillis() - startTime;

                    metricsPort.incrementSuccess();

                    CrawlResult result = CrawlResult.fromSuccess(
                            urlString, data, attempt, duration, attempt > 1
                    );
                    persistencePort.saveResult(result);

                    taskLog.infoWithContext("URL processada com sucesso via {}", Map.of(
                            "url", urlString,
                            "source", page.source(),
                            "attempt", attempt,
                            "durationMs", duration,
                            "hasDiscount", data.discountPrice() != null
                    ));

                    checkpointIfNeeded();
                    healthCheck.update();
                    totalProcessed.incrementAndGet();
                    return;

                } else if (page != null && page.isCloudflareBlocked()) {
                    throw new RuntimeException("Cloudflare bloqueou a requisicao");
                } else {
                    throw new RuntimeException("Pagina nao retornou conteudo");
                }

            } catch (Exception e) {
                lastException = e;
                taskLog.warnWithContext("Falha na tentativa {} para {}",
                        Map.of("attempt", attempt, "error", e.getMessage(), "url", urlString));

                metricsPort.recordRetry(urlString, attempt);

                if (retryPolicy.shouldRetry(e, attempt)) {
                    Duration backoff = retryPolicy.getBackoffForAttempt(attempt);
                    try {
                        Thread.sleep(backoff.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    taskLog.warn("Numero maximo de tentativas atingido ou erro nao retentavel");
                    break;
                }
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        metricsPort.incrementError();
        totalProcessed.incrementAndGet();
        String errorMsg = lastException != null ? lastException.getMessage() : "Falha desconhecida";

        ProductData errorData = ProductData.error(urlObj, errorMsg);
        CrawlResult result = CrawlResult.fromError(urlString, errorData, retryPolicy.getMaxRetries(), duration, true);
        persistencePort.saveResult(result);

        taskLog.errorWithContext("URL falhou apos todas as tentativas",
                Map.of("url", urlString, "attempts", retryPolicy.getMaxRetries(), "durationMs", duration));

        checkpointIfNeeded();
        healthCheck.update();
    }

    private FetchedPage fetchWithFallback(String url, StructuredLogger taskLog) {
        List<String> attempted = new java.util.ArrayList<>();

        for (CrawlerPort crawler : crawlerPorts) {
            if (shutdownRequested) return null;

            try {
                taskLog.debug("Tentando crawler: {}", crawler.name());
                Optional<FetchedPage> result = crawler.fetchPage(url);

                if (result.isPresent()) {
                    FetchedPage page = result.get();

                    if (page.isSuccess()) {
                        taskLog.info("Crawler '{}' resolveu com sucesso ({}b)", crawler.name(), page.html().length());
                        return page;
                    }

                    if (page.isCloudflareBlocked()) {
                        taskLog.warn("Crawler '{}' esbarrou em Cloudflare", crawler.name());
                    } else {
                        taskLog.warn("Crawler '{}' retornou HTTP {}", crawler.name(), page.statusCode());
                    }
                } else {
                    taskLog.warn("Crawler '{}' retornou vazio", crawler.name());
                }

                attempted.add(crawler.name());

            } catch (Exception e) {
                taskLog.warn("Crawler '{}' lancou excecao: {}", crawler.name(), e.getMessage());
                attempted.add(crawler.name() + "(erro:" + e.getMessage() + ")");
            }
        }

        taskLog.error("Todos os crawlers falharam para URL: {}. Tentados: {}", url, attempted);
        return null;
    }

    private synchronized void checkpointIfNeeded() {
        int processed = processedSinceLastCheckpoint.incrementAndGet();
        if (processed >= checkpointInterval) {
            processedSinceLastCheckpoint.set(0);

            long succ = metricsPort.getSuccessCount();
            long err = metricsPort.getErrorCount();
            double successRate = (succ + err) > 0 ? (double) succ / (succ + err) * 100 : 0;

            log.infoWithContext("Progresso do crawler", Map.of(
                    "success", succ,
                    "error", err,
                    "successRate", String.format("%.2f%%", successRate)
            ));
        }
    }
}
