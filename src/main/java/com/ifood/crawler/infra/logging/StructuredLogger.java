package com.ifood.crawler.infra.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        try (var ctx = new MDCContext(Map.of("correlationId", correlationId))) {
            logger.info(message, args);
        }
    }

    public void infoWithContext(String message, Map<String, Object> context, Object... args) {
        var allContext = new HashMap<>(context);
        allContext.put("correlationId", correlationId);
        try (var ctx = new MDCContext(allContext)) {
            logger.info(message, args);
        }
    }

    public void warn(String message, Object... args) {
        try (var ctx = new MDCContext(Map.of("correlationId", correlationId))) {
            logger.warn(message, args);
        }
    }

    public void warn(String message, Throwable t, Object... args) {
        try (var ctx = new MDCContext(Map.of("correlationId", correlationId))) {
            logger.warn(message, args, t);
        }
    }

    public void warnWithContext(String message, Map<String, Object> context, Object... args) {
        var allContext = new HashMap<>(context);
        allContext.put("correlationId", correlationId);
        try (var ctx = new MDCContext(allContext)) {
            logger.warn(message, args);
        }
    }

    public void error(String message, Object... args) {
        try (var ctx = new MDCContext(Map.of("correlationId", correlationId))) {
            logger.error(message, args);
        }
    }

    public void error(String message, Throwable t, Object... args) {
        try (var ctx = new MDCContext(Map.of("correlationId", correlationId))) {
            logger.error(message, args, t);
        }
    }

    public void errorWithContext(String message, Map<String, Object> context, Object... args) {
        var allContext = new HashMap<>(context);
        allContext.put("correlationId", correlationId);
        try (var ctx = new MDCContext(allContext)) {
            logger.error(message, args);
        }
    }

    public void debug(String message, Object... args) {
        if (logger.isDebugEnabled()) {
            try (var ctx = new MDCContext(Map.of("correlationId", correlationId))) {
                logger.debug(message, args);
            }
        }
    }

    public String getCorrelationId() {
        return correlationId;
    }

    private static class MDCContext implements AutoCloseable {
        private final Map<String, String> previousContext;

        @SuppressWarnings("unchecked")
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