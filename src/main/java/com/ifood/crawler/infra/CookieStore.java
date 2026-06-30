package com.ifood.crawler.infra;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ifood.crawler.infra.logging.StructuredLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CookieStore {

    private static final StructuredLogger log = StructuredLogger.getLogger(CookieStore.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path filePath;
    private final CopyOnWriteArrayList<StoredCookie> cookies = new CopyOnWriteArrayList<>();

    public record StoredCookie(String name, String value, String domain, String path) {}

    public CookieStore(Path filePath) {
        this.filePath = filePath;
        load();
    }

    public List<StoredCookie> getCookies() {
        return Collections.unmodifiableList(cookies);
    }

    public void updateCookies(List<StoredCookie> newCookies) {
        if (newCookies == null || newCookies.isEmpty()) return;
        for (StoredCookie newCookie : newCookies) {
            cookies.removeIf(c -> c.name().equals(newCookie.name()) && c.domain().equals(newCookie.domain()));
            cookies.add(newCookie);
        }
        save();
        log.info("CookieStore atualizado: {} cookies armazenados em {}", cookies.size(), filePath);
    }

    public void clear() {
        cookies.clear();
        save();
    }

    public ArrayNode toJson() {
        ArrayNode arr = MAPPER.createArrayNode();
        for (StoredCookie c : cookies) {
            ObjectNode obj = MAPPER.createObjectNode();
            obj.put("name", c.name());
            obj.put("value", c.value());
            obj.put("domain", c.domain());
            obj.put("path", c.path() != null ? c.path() : "/");
            arr.add(obj);
        }
        return arr;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        Path absPath = filePath.toAbsolutePath();
        if (!Files.exists(absPath)) return;
        try {
            String content = Files.readString(absPath);
            var rawList = MAPPER.readValue(content, List.class);
            List<StoredCookie> loaded = new ArrayList<>();
            for (var raw : rawList) {
                var map = (java.util.Map<String, Object>) raw;
                loaded.add(new StoredCookie(
                        (String) map.get("name"),
                        (String) map.get("value"),
                        (String) map.get("domain"),
                        (String) map.getOrDefault("path", "/")
                ));
            }
            cookies.addAll(loaded);
            log.info("CookieStore carregado: {} cookies de {}", cookies.size(), filePath);
        } catch (IOException e) {
            log.warn("Falha ao carregar CookieStore de {}: {}", filePath, e.getMessage());
        }
    }

    private void save() {
        try {
            Path absPath = filePath.toAbsolutePath();
            Path parent = absPath.getParent();
            if (parent != null) Files.createDirectories(parent);
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(absPath.toFile(), toJson());
        } catch (Exception e) {
            log.warn("Falha ao salvar CookieStore em {}: [{}] {}", filePath.toAbsolutePath(),
                    e.getClass().getSimpleName(), e.getMessage() != null ? e.getMessage() : "(null)");
        }
    }
}
