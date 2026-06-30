package com.ifood.crawler.adapter.output;

import com.ifood.crawler.core.model.FetchedPage;
import com.ifood.crawler.core.port.output.CrawlerPort;
import com.ifood.crawler.infra.CookieStore;
import com.ifood.crawler.infra.logging.StructuredLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.stream.Collectors;

public class SimpleHttpCrawler implements CrawlerPort {

    private static final StructuredLogger log = StructuredLogger.getLogger(SimpleHttpCrawler.class);
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final CookieStore cookieStore;
    private final Duration requestTimeout;

    public SimpleHttpCrawler(CookieStore cookieStore, Duration requestTimeout) {
        this.cookieStore = cookieStore;
        this.requestTimeout = requestTimeout;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        log.info("SimpleHttpCrawler iniciado: timeout={}s", requestTimeout.toSeconds());
    }

    @Override
    public String name() {
        return "simple-http";
    }

    @Override
    public Optional<FetchedPage> fetchPage(String url) {
        if (cookieStore.getCookies().isEmpty()) {
            return Optional.empty();
        }

        long start = System.currentTimeMillis();
        try {
            String cookieHeader = cookieStore.getCookies().stream()
                    .map(c -> c.name() + "=" + c.value())
                    .collect(Collectors.joining("; "));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(requestTimeout)
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Cookie", cookieHeader)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            int status = response.statusCode();
            String html = response.body();

            if (status < 200 || status >= 400) {
                log.warn("SimpleHttp HTTP {} para {} ({}ms)", status, url, elapsed);
                return Optional.empty();
            }

            if (html == null || html.isBlank()) {
                log.warn("SimpleHttp HTML vazio para {} ({}ms)", url, elapsed);
                return Optional.empty();
            }

            if (html.contains("Just a moment") || html.contains("challenge-platform")) {
                log.info("SimpleHttp: Cloudflare detectado (cookies podem ter expirado) para {} ({}ms)", url, elapsed);
                return Optional.empty();
            }

            FetchedPage fetched = new FetchedPage(url, html, status, "simple-http");
            log.info("SimpleHttp resolveu {} ({}ms, {}b)", url, elapsed, html.length());
            return Optional.of(fetched);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("SimpleHttp exception para {} apos {}ms: {}", url, elapsed, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void close() {
    }
}
