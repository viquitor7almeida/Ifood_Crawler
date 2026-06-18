package main.java.com.ifood.crawler.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Map;
import java.util.UUID;

/**
 * Wrapper para logging estruturado com MDC.
 * Permite adicionar campos contextuais a cada log.
 */
public class StructuredLogger {

    private final Logger logger;
    private final String correlationId;

    private StructuredLogger(Class<?> clazz, String correlationId) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.correlationId = correlationId;
    }

    public static StructuredLogger getLogger(Class<?> clazz) {
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        return new StructuredLogger(clazz, correlationId);
    }

    public static StructuredLogger getLogger(Class<?> clazz, String correlationId) {
        return new StructuredLogger(clazz, correlationId);
    }

    public void info(String message, Object... args) {
        try (var ctx = new MDCContext(Map.of(
                "correlationId", correlationId,
                "level", "INFO"
        ))) {
            logger.info(message, args);
        }
    }

    public void infoWithContext(String message, Map<String, Object> context, Object... args) {
        var allContext = new java.util.HashMap<>(context);
        allContext.put("correlationId", correlationId);
        try (var ctx = new MDCContext(allContext)) {
            logger.info(message, args);
        }
    }

    public void warn(String message, Object... args) {
        try (var ctx = new MDCContext(Map.of(
                "correlationId", correlationId,
                "level", "WARN"
        ))) {
            logger.warn(message, args);
        }
    }

    public void warn(String message, Throwable t, Object... args) {
        try (var ctx = new MDCContext(Map.of(
                "correlationId", correlationId,
                "level", "WARN"
        ))) {
            logger.warn(message, args, t);
        }
    }

    public void error(String message, Object... args) {
        try (var ctx = new MDCContext(Map.of(
                "correlationId", correlationId,
                "level", "ERROR"
        ))) {
            logger.error(message, args);
        }
    }

    public void error(String message, Throwable t, Object... args) {
        try (var ctx = new MDCContext(Map.of(
                "correlationId", correlationId,
                "level", "ERROR"
        ))) {
            logger.error(message, args, t);
        }
    }

    public void debug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            try (var ctx = new MDCContext(Map.of(
                    "correlationId", correlationId,
                    "level", "DEBUG"
            ))) {
                logger.debug(message, args);
            }
        }
    }

    public String getCorrelationId() {
        return correlationId;
    }

    // AutoCloseable para limpar MDC após cada log
    private static class MDCContext implements AutoCloseable {
        private final Map<String, String> previousContext;

        public MDCContext(Map<String, Object> context) {
            this.previousContext = MDC.getCopyOfContextMap();
            context.forEach((key, value) -> MDC.put(key, String.valueOf(value)));
        }

        @Override
        public void close() {
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            } else {
                MDC.clear();
            }
        }
    }
}