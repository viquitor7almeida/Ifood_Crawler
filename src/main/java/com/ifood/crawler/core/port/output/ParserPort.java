package com.ifood.crawler.core.port.output;

import com.ifood.crawler.core.model.ProductData;
import java.net.URL;

public interface ParserPort {
    ProductData parse(String html, URL originalUrl);
}