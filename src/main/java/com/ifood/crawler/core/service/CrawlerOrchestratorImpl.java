package main.java.com.ifood.crawler.core.service;

import com.ifood.crawler.core.model.CrawlResult;
import com.ifood.crawler.core.model.ExecutionSummary;
import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.input.CrawlerOrchestrator;
import com.ifood.crawler.core.port.input.UrlProvider;
import com.ifood.crawler.core.port.output.*;
import com.ifood.crawler.infra.health.HealthCheck;
import com.ifood.crawler.infra.logging.StructuredLogger;
import com.microsoft.playwright.Page;

import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 *orquestrador principal do crawler.
 *gerencia concorrência, rate limiting, retry, checkpoint e metricas.
 */
public class CrawlerOrchestratorImpl implements CrawlerOrchestrator {

    private final StructuredLogger log = StructuredLogger.getLogger(CrawlerOrchestratorImpl.class);

    private final UrlProvider urlProvider;
    private final CrawlerPort crawlerPort;
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
    private volatile boolean shutdownRequested = false;

    public CrawlerOrchestratorImpl(UrlProvider urlProvider,
                                   CrawlerPort crawlerPort,
                                   ParserPort parserPort,
                                   PersistencePort persistencePort,
                                   MetricsPort metricsPort,
                                   HealthCheck healthCheck,
                                   RetryPolicy retryPolicy,
                                   TokenBucketRateLimiter rateLimiter,
                                   int parallelism,
                                   int checkpointInterval) {
        this.urlProvider = urlProvider;
        this.crawlerPort = crawlerPort;
        this.parserPort = parserPort;
        this.persistencePort = persistencePort;
        this.metricsPort = metricsPort;
        this.healthCheck = healthCheck;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.parallelism = parallelism;
        this.checkpointInterval = checkpointInterval;
        this.workerPool = Executors.newFixedThreadPool(parallelism);
        
        //registrar shutdown hook para graceful shutdown
        registerShutdownHook();
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Graceful shutdown iniciado...");
            shutdownRequested = true;
            
            //aguarda workers finalizarem
            if (!workerPool.isShutdown()) {
                workerPool.shutdown();
                try {
                    if (!workerPool.awaitTermination(30, TimeUnit.SECONDS)) {
                        log.warn("Forçando shutdown de workers após timeout");
                        workerPool.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    workerPool.shutdownNow();
                }
            }
            
            //salva checkpoint final
            try {
                persistencePort.exportFinalResults(persistencePort.getAllResults());
                log.info("Checkpoint final salvo com sucesso");
            } catch (Exception e) {
                log.error("Falha ao salvar checkpoint final", e);
            }
            
            //fecha recursos do Playwright
            crawlerPort.close();
            log.info("Graceful shutdown concluído.");
        }));
    }

    @Override
    public ExecutionSummary run() {
        Instant start = Instant.now();
        long totalUrls = urlProvider.totalUrls();
        log.info("Iniciando crawler com {} URLs, paralelismo {}, maxRetries {}", 
                totalUrls, parallelism, retryPolicy.getMaxRetries());

        try (Stream<String> urlStream = urlProvider.urls()) {
            urlStream.forEach(url -> {
                if (shutdownRequested) return;
                
                if (persistencePort.isAlreadyProcessed(url)) {
                    log.debug("URL já processada com sucesso, ignorando: {}", url);
                    return;
                }
                workerPool.submit(() -> processWithRetry(url));
            });
        }

        //aguarda termino de todas as tarefas ou shutdown
        workerPool.shutdown();
        try {
            if (!workerPool.awaitTermination(2, TimeUnit.HOURS)) {
                log.warn("Timeout aguardando workers, forçando shutdown...");
                workerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            workerPool.shutdownNow();
        }

        Instant end = Instant.now();
        Duration totalDuration = Duration.between(start, end);
        
        //gera relatorio final
        ExecutionSummary summary = ExecutionSummary.of(
                totalUrls,
                metricsPort.getSuccessCount(),
                metricsPort.getErrorCount(),
                totalDuration,
                start, end
        );
        
        //exporta resultados finais
        try {
            persistencePort.exportFinalResults(persistencePort.getAllResults());
            log.info("Resultados finais exportados com sucesso");
        } catch (Exception e) {
            log.error("Falha ao exportar resultados finais", e);
        }
        
        // Log do resumo
        log.info(summary.toFormattedString());
        log.info(metricsPort.generateReport());
        
        // Atualiza health check final
        healthCheck.update();
        
        return summary;
    }

    /**
     *processa uma URL com retry policy.
     *cada URL tem seu próprio correlation ID para rastreabilidade.
     */
    private void processWithRetry(String urlString) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        StructuredLogger taskLog = StructuredLogger.getLogger(CrawlerOrchestratorImpl.class, correlationId);
        
        long startTime = System.currentTimeMillis();
        Exception lastException = null;
        URL urlObj;
        
        try {
            urlObj = new URL(urlString);
        } catch (Exception e) {
            taskLog.error("URL inválida: {}", e.getMessage());
            metricsPort.incrementError();
            persistencePort.saveResult(CrawlResult.fromError(urlString,
                    ProductData.error(null, "URL inválida: " + e.getMessage()), 1, 0, false));
            healthCheck.update();
            return;
        }

        taskLog.infoWithContext("Iniciando processamento da URL", Map.of(
                "url", urlString,
                "maxRetries", retryPolicy.getMaxRetries()
        ));

        for (int attempt = 1; attempt <= retryPolicy.getMaxRetries(); attempt++) {
            if (shutdownRequested) {
                taskLog.warn("Shutdown solicitado, abortando processamento de: {}", urlString);
                return;
            }

            try {
                //rate limiting - adquire token (bloqueia até disponível)
                taskLog.debug("Aguardando token de rate limit, tentativa {}", attempt);
                rateLimiter.acquire();
                
                //executa fetch com timeout da política
                taskLog.debug("Buscando página, tentativa {}", attempt);
                Optional<Page> pageOpt = crawlerPort.fetchPage(urlString);
                
                if (pageOpt.isPresent()) {
                    try (Page page = pageOpt.get()) {
                        // Extrai dados
                        ProductData data = parserPort.parse(page, urlObj);
                        long duration = System.currentTimeMillis() - startTime;
                        
                        //registra sucesso
                        metricsPort.incrementSuccess();
                        metricsPort.recordDuration(urlString, duration);
                        
                        CrawlResult result = CrawlResult.fromSuccess(
                                urlString, data, attempt, duration, attempt > 1
                        );
                        persistencePort.saveResult(result);
                        
                        taskLog.infoWithContext("URL processada com sucesso", Map.of(
                                "url", urlString,
                                "attempt", attempt,
                                "durationMs", duration,
                                "hasDiscount", data.discountPrice() != null
                        ));
                        
                        checkpointIfNeeded();
                        healthCheck.update();
                        return;
                    }
                } else {
                    throw new RuntimeException("Página não retornou conteúdo (possível timeout)");
                }
                
            } catch (Exception e) {
                lastException = e;
                taskLog.warnWithContext("Falha na tentativa {} para {}", 
                        Map.of("attempt", attempt, "error", e.getMessage(), "url", urlString));
                
                metricsPort.recordRetry(urlString, attempt);
                
                //decide se deve tentar novamente
                if (retryPolicy.shouldRetry(e, attempt)) {
                    Duration backoff = retryPolicy.getBackoffForAttempt(attempt);
                    taskLog.infoWithContext("Aguardando {}ms antes de retentar", 
                            Map.of("backoffMs", backoff.toMillis(), "attempt", attempt));
                    try {
                        Thread.sleep(backoff.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        taskLog.warn("Thread interrompida durante backoff");
                        break;
                    }
                } else {
                    taskLog.warn("Número máximo de tentativas atingido ou erro não retentável");
                    break;
                }
            }
        }

        //se chegou aqui, falhou apos todas as tentativas
        long duration = System.currentTimeMillis() - startTime;
        metricsPort.incrementError();
        String errorMsg = lastException != null ? lastException.getMessage() : "Falha desconhecida";
        
        ProductData errorData = ProductData.error(urlObj, errorMsg);
        CrawlResult result = CrawlResult.fromError(urlString, errorData, retryPolicy.getMaxRetries(), duration, true);
        persistencePort.saveResult(result);
        
        taskLog.errorWithContext("URL falhou após todas as tentativas", 
                Map.of("url", urlString, "attempts", retryPolicy.getMaxRetries(), "durationMs", duration));
        
        checkpointIfNeeded();
        healthCheck.update();
    }

    /**
     *salva checkpoint a cada N URLs processadas.
     *sincronizado para evitar race conditions.
     */
    private synchronized void checkpointIfNeeded() {
        int processed = processedSinceLastCheckpoint.incrementAndGet();
        if (processed >= checkpointInterval) {
            processedSinceLastCheckpoint.set(0);
            log.debug("Checkpoint realizado após {} URLs", checkpointInterval);
            
            // Atualiza métricas de throughput
            double successRate = (metricsPort.getSuccessCount() + metricsPort.getErrorCount()) > 0 ?
                    (double) metricsPort.getSuccessCount() / 
                    (metricsPort.getSuccessCount() + metricsPort.getErrorCount()) * 100 : 0;
            
            log.infoWithContext("Progresso do crawler", Map.of(
                    "success", metricsPort.getSuccessCount(),
                    "error", metricsPort.getErrorCount(),
                    "successRate", String.format("%.2f%%", successRate)
            ));
        }
    }
}