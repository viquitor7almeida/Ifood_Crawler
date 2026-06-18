package com.ifood.crawler.core.port.input;

import java.util.stream.Stream;

public interface UrlProvider {
    Stream<String> urls();
    long totalUrls();
}