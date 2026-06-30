package com.ifood.crawler.core.model;

import java.util.Objects;

public record FetchedPage(
    String url,
    String html,
    int statusCode,
    String source
) {
    public FetchedPage {
        Objects.requireNonNull(url, "url nao pode ser nulo");
        Objects.requireNonNull(html, "html nao pode ser nulo");
        if (statusCode < 100) throw new IllegalArgumentException("statusCode invalido: " + statusCode);
    }

    public boolean isCloudflareBlocked() {
        return html.contains("Just a moment")
            || html.contains("jschl-answer")
            || html.contains("challenge-platform")
            || html.contains("cf-browser-verification")
            || html.contains("Uma momento");
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300 && !isCloudflareBlocked();
    }
}
