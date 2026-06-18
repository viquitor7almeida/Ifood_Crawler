package com.ifood.crawler.adapter.output;

import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.output.ParserPort;
import com.microsoft.playwright.Page;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class ResilientParser implements ParserPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientParser.class);

    // SELETORES REAIS BASEADOS NO HTML QUE VOCÊ MANDOU
    private static final List<String> TITLE_SELECTORS = Arrays.asList(
            ".product-detail__description",           // Classe principal
            "div.product-detail__description",        // Mais específico
            "[data-test-id='product-detail'] .product-detail__description",
            "h1"                                      // Fallback
    );

    private static final List<String> NORMAL_PRICE_SELECTORS = Arrays.asList(
            ".product-card__price--original",         // Preço original (R$ 25,50)
            "span.product-card__price--original",
            "[data-test-id='product-card-price'] .product-card__price--original"
    );

    private static final List<String> DISCOUNT_PRICE_SELECTORS = Arrays.asList(
            ".product-card__price--discount",         // Preço com desconto (R$ 24,23)
            "span.product-card__price--discount",
            ".product-card__price > span"             // Fallback
    );

    private static final List<String> IMAGE_SELECTORS = Arrays.asList(
            ".product-detail__image",                 // Imagem principal
            "img.product-detail__image",
            ".product-detail__image-container img",
            "img[class*='product-detail__image']"
    );

    @Override
    public ProductData parse(Page page, URL originalUrl) {
        // Extrai os dados com os seletores reais
        String title = extractWithFallback(page, TITLE_SELECTORS);
        String normalPrice = extractPrice(page, NORMAL_PRICE_SELECTORS);
        String discountPrice = extractPrice(page, DISCOUNT_PRICE_SELECTORS);
        String imageUrl = extractAttribute(page, IMAGE_SELECTORS, "src");

        // Fallback para JSoup se algo falhar
        if (allNull(title, normalPrice, discountPrice, imageUrl)) {
            String html = page.content();
            Document doc = Jsoup.parse(html);
            title = extractTextFromJsoup(doc, TITLE_SELECTORS);
            normalPrice = extractPriceFromJsoup(doc, NORMAL_PRICE_SELECTORS);
            discountPrice = extractPriceFromJsoup(doc, DISCOUNT_PRICE_SELECTORS);
            imageUrl = extractAttributeFromJsoup(doc, IMAGE_SELECTORS, "src");
        }

        try {
            return ProductData.success(
                    title,
                    normalPrice,
                    discountPrice,
                    originalUrl,
                    imageUrl != null ? new URL(imageUrl) : null
            );
        } catch (Exception e) {
            log.warn("Erro ao criar URL da imagem: {}", e.getMessage());
            return ProductData.success(title, normalPrice, discountPrice, originalUrl, null);
        }
    }

    private boolean allNull(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) return false;
        }
        return true;
    }

    // ---------- Métodos auxiliares com Playwright ----------
    private String extractWithFallback(Page page, List<String> selectors) {
        for (String selector : selectors) {
            try {
                if (page.isVisible(selector)) {
                    String text = page.textContent(selector);
                    if (text != null && !text.isBlank()) return text.trim();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractPrice(Page page, List<String> selectors) {
        String raw = extractWithFallback(page, selectors);
        if (raw == null) return null;
        // Limpa formatação: remove "R$", espaços, e mantém apenas números e vírgula/ponto
        return raw.replaceAll("[^\\d,.]", "").replace(",", ".").trim();
    }

    private String extractAttribute(Page page, List<String> selectors, String attribute) {
        for (String selector : selectors) {
            try {
                String attr = page.getAttribute(selector, attribute);
                if (attr != null && !attr.isBlank()) return attr;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ---------- Fallback com JSoup ----------
    private String extractTextFromJsoup(Document doc, List<String> selectors) {
        for (String selector : selectors) {
            var element = doc.selectFirst(selector);
            if (element != null && !element.text().isBlank()) {
                return element.text().trim();
            }
        }
        return null;
    }

    private String extractPriceFromJsoup(Document doc, List<String> selectors) {
        String text = extractTextFromJsoup(doc, selectors);
        if (text == null) return null;
        return text.replaceAll("[^\\d,.]", "").replace(",", ".");
    }

    private String extractAttributeFromJsoup(Document doc, List<String> selectors, String attribute) {
        for (String selector : selectors) {
            var element = doc.selectFirst(selector);
            if (element != null) {
                String attr = element.attr(attribute);
                if (!attr.isBlank()) return attr;
            }
        }
        return null;
    }
}