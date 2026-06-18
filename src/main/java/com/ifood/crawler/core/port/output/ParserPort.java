package com.ifood.crawler.core.port.output;

import com.ifood.crawler.core.model.ProductData;
import com.microsoft.playwright.Page;
import java.net.URL;

public interface ParserPort {
    ProductData parse(Page page, URL originalUrl);
}