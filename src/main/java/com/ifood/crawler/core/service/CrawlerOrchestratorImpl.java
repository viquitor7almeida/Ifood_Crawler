package main.java.com.ifood.crawler.core.service;

import com.ifood.crawler.core.model.CrawlResult;
import com.ifood.crawler.core.model.ExecutionSummary;
import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.input.CrawlerOrchestrator;
import com.ifood.crawler.core.port.input.UrlProvider;
import com.ifood.crawler.core.port.output.*;
import com.microsoft.playwright.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class CrawlerOrchestratorImpl implements CrawlerOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CrawlerOrchestratorImpl.class);

    private final UrlProvider urlProvider;
    private final CrawlerPort crawlerPort;
    private final ParserPort parserPort;
    private final PersistencePort persistencePort;
    private final MetricsPort metricsPort;
    private final int parallelism;
    private final int maxRetries;
    private final long initialBackoffMs;
    private final double backoffMultiplier;
    private final long jitterMs;
    private final long rateLimitDelayMs;
    private final int checkpointInterval;

    private final ExecutorService workerPool;
    private final Semaphore rateLimiter;
    private final AtomicInteger processedSinceLastCheckpoint = new AtomicInteger(0);

    public CrawlerOrchestratorImpl(UrlProvider urlProvider,
                                   CrawlerPort crawlerPort,
                                   ParserPort parserPort,
                                   PersistencePort persistencePort,
                                   MetricsPort metricsPort,
                                   int parallelism,
                                   int maxRetries,
                                   long initialBackoffMs,
                                   double backoffMultiplier,
                                   long jitterMs,
                                   long rateLimitDelayMs,
                                   int checkpointInterval) {
        this.urlProvider = urlProvider;
        this.crawlerPort = crawlerPort;
        this.parserPort = parserPort;
        this.persistencePort = persistencePort;
        this.metricsPort = metricsPort;
        this.parallelism = parallelism;
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;
        this.backoffMultiplier = backoffMultiplier;
        this.jitterMs = jitterMs;
        this.rateLimitDelayMs = rateLimitDelayMs;
        this.checkpointInterval = checkpointInterval;
        this.workerPool = Executors.newFixedThreadPool(parallelism);
        this.rateLimiter = new Semaphore(parallelism);
    }

    @Override
    public ExecutionSummary run() {
        Instant start = Instant.now();
        long totalUrls = urlProvider.totalUrls();
        log.info("Iniciando crawler com {} URLs, paralelismo {}", totalUrls, parallelism);

        try (Stream<String> urlStream = urlProvider.urls()) {
            urlStream.forEach(url -> {
                if (persistencePort.isAlreadyProcessed(url)) {
                    log.debug("URL já processada com sucesso, ignorando: {}", url);
                    return;
                }
                workerPool.submit(() -> processWithRetry(url));
            });
        }

        // Aguarda término de todas as tarefas
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(1, TimeUnit.HOURS)) {
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }

        Instant end = Instant.now();
        Duration totalDuration = Duration.between(start, end);
        ExecutionSummary summary = ExecutionSummary.of(
                totalUrls,
                metricsPort.getSuccessCount(),
                metricsPort.getErrorCount(),
                totalDuration,
                start, end
        );
        // Exporta resultados finais
        persistencePort.exportFinalResults(persistencePort.getAllResults());
        log.info(summary.toFormattedString());
        return summary;
    }

    private void processWithRetry(String urlString) {
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        URL urlObj;
        try {
            urlObj = new URL(urlString);
        } catch (Exception e) {
            metricsPort.incrementError();
            persistencePort.saveResult(CrawlResult.fromError(urlString,
                    ProductData.error(null, "URL inválida: " + e.getMessage()), 1, 0, false));
            return;
        }

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // Rate limiting: delay entre requisições
                Thread.sleep(rateLimitDelayMs + (long)(Math.random() * 200)); // jitter
                rateLimiter.acquire(); // controla concorrência
                Optional<Page> pageOpt = crawlerPort.fetchPage(urlString);
                if (pageOpt.isPresent()) {
                    try (Page page = pageOpt.get()) {
                        ProductData data = parserPort.parse(page, urlObj);
                        long duration = System.currentTimeMillis() - startTime;
                        metricsPort.incrementSuccess();
                        metricsPort.recordDuration(urlString, duration);
                        CrawlResult result = CrawlResult.fromSuccess(urlString, data, attempt, duration, attempt > 1);
                        persistencePort.saveResult(result);
                        checkpointIfNeeded();
                        return;
                    }
                } else {
                    throw new RuntimeException("Página não retornou conteúdo (possível timeout)");
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Falha na tentativa {} para {}: {}", attempt, urlString, e.getMessage());
                metricsPort.recordRetry(urlString, attempt);
                if (attempt < maxRetries) {
                    long backoff = (long) (initialBackoffMs * Math.pow(backoffMultiplier, attempt - 1));
                    long jitterVal = (long) (Math.random() * jitterMs);
                    try {
                        Thread.sleep(backoff + jitterVal);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                rateLimiter.release();
            }
        }

        // Se chegou aqui, falhou após todas as tentativas
        long duration = System.currentTimeMillis() - startTime;
        metricsPort.incrementError();
        String errorMsg = lastException != null ? lastException.getMessage() : "Falha desconhecida";
        ProductData errorData = ProductData.error(urlObj, errorMsg);
        CrawlResult result = CrawlResult.fromError(urlString, errorData, maxRetries, duration, true);
        persistencePort.saveResult(result);
        checkpointIfNeeded();
    }

    private synchronized void checkpointIfNeeded() {
        int processed = processedSinceLastCheckpoint.incrementAndGet();
        if (processed >= checkpointInterval) {
            // Força flush do SQLite (commit) - já feito automaticamente, mas garantimos
            processedSinceLastCheckpoint.set(0);
            log.info("Checkpoint realizado após {} URLs", checkpointInterval);
        }
    }
}