package com.ifood.crawler.core.model;

import java.net.URL;
import java.util.Objects;

public record ProductData(
    String title,
    String normalPrice,
    String discountPrice,
    URL productUrl,
    URL imageUrl,
    CrawlStatus status,
    String errorMessage
) {
    
    public ProductData {
        Objects.requireNonNull(productUrl, "productUrl não pode ser nulo");
        Objects.requireNonNull(status, "status não pode ser nulo");
        if (status == CrawlStatus.ERROR && (errorMessage == null || errorMessage.isBlank())) {
            throw new IllegalArgumentException("Status ERROR requer mensagem de erro não vazia");
        }
    }
    
    public static ProductData success(
            String title, String normalPrice, String discountPrice,
            URL productUrl, URL imageUrl) {
        return new ProductData(title, normalPrice, discountPrice, productUrl, imageUrl, CrawlStatus.SUCCESS, null);
    }
    
    public static ProductData error(URL productUrl, String errorMessage) {
        return new ProductData(null, null, null, productUrl, null, CrawlStatus.ERROR, errorMessage);
    }
}