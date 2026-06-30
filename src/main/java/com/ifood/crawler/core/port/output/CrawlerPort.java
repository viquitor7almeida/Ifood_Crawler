package com.ifood.crawler.core.port.output;

import com.ifood.crawler.core.model.FetchedPage;
import java.util.Optional;

public interface CrawlerPort {
    String name();
    Optional<FetchedPage> fetchPage(String url);
    void close();
}