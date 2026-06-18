package com.ifood.crawler.core.port.output;

import com.ifood.crawler.core.model.CrawlResult;
import java.util.List;

public interface PersistencePort {
    boolean isAlreadyProcessed(String url);
    void saveResult(CrawlResult result);
    List<CrawlResult> getAllResults();
    void exportFinalResults(List<CrawlResult> results);
}