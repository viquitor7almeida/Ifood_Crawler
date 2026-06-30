package com.ifood.crawler.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifood.crawler.core.model.FetchedPage;
import com.ifood.crawler.core.port.output.CrawlerPort;
import com.ifood.crawler.infra.CookieStore;
import com.ifood.crawler.infra.logging.StructuredLogger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class FlaresolverrCrawler implements CrawlerPort {

    private static final StructuredLogger log = StructuredLogger.getLogger(FlaresolverrCrawler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String apiUrl;
    private final Duration requestTimeout;
    private final CookieStore cookieStore;
    private final String sessionId;
    private final HttpClient httpClient;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong circuitOpenedAt = new AtomicLong(0);
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;
    private static final long CIRCUIT_BREAKER_COOLDOWN_MS = 120_000;

    public FlaresolverrCrawler(String flaresolverrUrl, Duration requestTimeout, CookieStore cookieStore) {
        this.requestTimeout = requestTimeout;
        this.cookieStore = cookieStore;
        this.apiUrl = flaresolverrUrl.endsWith("/v1") ? flaresolverrUrl : flaresolverrUrl + "/v1";
        this.sessionId = "crawler-" + UUID.randomUUID().toString().substring(0, 8);

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        log.info("FlaresolverrCrawler iniciado: url={}, timeout={}s, session={}, cookies={}",
                apiUrl, requestTimeout.toSeconds(), sessionId, cookieStore.getCookies().size());
    }

    @Override
    public String name() {
        return "flaresolverr";
    }

    public void prime() {
        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("cmd", "request.get");
            body.put("url", "https://example.com");
            body.put("maxTimeout", 15000);
            body.put("session", sessionId);
            String json = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.info("Flaresolverr primed (session={})", sessionId);
        } catch (Exception e) {
            log.debug("Flaresolverr prime ignorado: {}", e.getMessage());
        }
    }

    @Override
    public Optional<FetchedPage> fetchPage(String url) {
        if (isCircuitOpen()) {
            log.warn("Flaresolverr circuit OPEN, skipping {}", url);
            return Optional.empty();
        }

        if (!cookieStore.getCookies().isEmpty()) {
            Optional<FetchedPage> fast = tryFetch(url, true);
            if (fast.isPresent() && fast.get().isSuccess()) {
                consecutiveFailures.set(0);
                return fast;
            }
        }

        Optional<FetchedPage> result = tryFetch(url, false);
        if (result.isPresent() && result.get().isSuccess()) {
            consecutiveFailures.set(0);
            return result;
        }

        int fails = consecutiveFailures.incrementAndGet();
        if (fails >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpenedAt.set(System.currentTimeMillis());
            log.warn("Flaresolverr circuit OPEN after {} consecutive failures (cooldown={}s)",
                    fails, CIRCUIT_BREAKER_COOLDOWN_MS / 1000);
        }

        return result;
    }

    private boolean isCircuitOpen() {
        long opened = circuitOpenedAt.get();
        if (opened == 0) return false;
        if (System.currentTimeMillis() - opened > CIRCUIT_BREAKER_COOLDOWN_MS) {
            circuitOpenedAt.set(0);
            consecutiveFailures.set(0);
            log.info("Flaresolverr circuit recovered after cooldown");
            return false;
        }
        return true;
    }

    private Optional<FetchedPage> tryFetch(String url, boolean withCookies) {
        long start = System.currentTimeMillis();
        HttpResponse<String> httpResponse = null;

        try {
            ObjectNode body = MAPPER.createObjectNode();
            body.put("cmd", "request.get");
            body.put("url", url);
            body.put("maxTimeout", requestTimeout.toMillis());
            body.put("session", sessionId);
            if (withCookies) {
                body.set("cookies", cookieStore.toJson());
            }

            String jsonBody = MAPPER.writeValueAsString(body);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(requestTimeout.plus(Duration.ofSeconds(10)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            long elapsed = System.currentTimeMillis() - start;

            String responseBody = httpResponse.body();

            if (httpResponse.statusCode() != 200) {
                log.warn("Flaresolverr HTTP {} para {} ({}ms)", httpResponse.statusCode(), url, elapsed);
                return Optional.empty();
            }

            if (responseBody != null && !responseBody.isEmpty()) {
                int maxLen = Math.min(responseBody.length(), 500);
                log.debug("Flaresolverr response ({} total, first {}b): {}", responseBody.length(), maxLen,
                        responseBody.substring(0, maxLen));
            }

            JsonNode root = MAPPER.readTree(responseBody);
            String status = root.path("status").asText();

            if (!"ok".equals(status)) {
                String msg = root.path("message").asText("");
                String diagnostic = diagnosticFromError(msg);
                log.warn("Flaresolverr erro: status={}, diagnostic={} ({}ms)", status, diagnostic, elapsed);
                return Optional.empty();
            }

            JsonNode solution = root.path("solution");
            String html = solution.path("response").asText();
            int statusCode = solution.path("status").asInt(200);

            if (html == null || html.isBlank()) {
                log.warn("Flaresolverr HTML vazio para {} ({}ms)", url, elapsed);
                return Optional.empty();
            }

            if (solution.has("cookies") && !solution.get("cookies").isNull()) {
                try {
                    List<CookieStore.StoredCookie> parsed = parseCookies(solution.get("cookies"));
                    cookieStore.updateCookies(parsed);
                } catch (Exception e) {
                    log.warn("Flaresolverr cookie save error (continuando): {}", e.getMessage());
                }
            }

            FetchedPage fetched = new FetchedPage(url, html, statusCode, "flaresolverr");
            log.info("Flaresolverr resolveu {} ({}ms, status={}, size={}b)", url, elapsed, statusCode, html.length());
            return Optional.of(fetched);

        } catch (java.net.http.HttpTimeoutException e) {
            log.warn("Flaresolverr timeout {}s para {}", requestTimeout.toSeconds(), url);
            return Optional.empty();
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.warn("Flaresolverr exception para {} apos {}ms: [{}] {}", url, elapsed,
                    e.getClass().getSimpleName(), e.getMessage() != null ? e.getMessage() : "(null)");
            log.debug("Flaresolverr exception stacktrace", e);
            if (httpResponse != null) {
                String body = httpResponse.body();
                log.debug("Flaresolverr response body ({}b): {}", body != null ? body.length() : 0,
                        body != null ? body.substring(0, Math.min(body.length(), 500)) : "null");
            }
            return Optional.empty();
        }
    }

    private List<CookieStore.StoredCookie> parseCookies(JsonNode cookiesNode) {
        List<CookieStore.StoredCookie> result = new ArrayList<>();
        if (cookiesNode.isArray()) {
            for (JsonNode c : cookiesNode) {
                String name = c.path("name").asText();
                String value = c.path("value").asText();
                String domain = c.path("domain").asText();
                String path = c.path("path").asText("/");
                if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                    result.add(new CookieStore.StoredCookie(name, value, domain, path));
                }
            }
        }
        return result;
    }

    private String diagnosticFromError(String message) {
        if (message.contains("Challenge detected")) {
            return "CLOUDFLARE_CHALLENGE - Flaresolverr detectou mas nao conseguiu resolver.";
        }
        if (message.contains("Timeout")) {
            return "TIMEOUT - Excedeu " + requestTimeout.toSeconds() + "s.";
        }
        if (message.contains("tab crashed")) {
            return "TAB_CRASHED - Cloudflare derrubou a aba do Chromium (provavelmente /dev/shm insuficiente).";
        }
        return message;
    }

    @Override
    public void close() {
        log.info("FlaresolverrCrawler fechado (session={})", sessionId);
    }
}
