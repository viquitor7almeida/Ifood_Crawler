package com.ifood.crawler.core.port.input;

import com.ifood.crawler.core.model.ExecutionSummary;

public interface CrawlerOrchestrator {
    ExecutionSummary run();
}