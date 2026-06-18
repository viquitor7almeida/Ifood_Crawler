package com.ifood.crawler.core.port.output;

import com.microsoft.playwright.Page;
import java.util.Optional;

public interface CrawlerPort {
    Optional<Page> fetchPage(String url) throws Exception;
    void close();
}