package main.java.com.ifood.crawler.adapter.output;

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
import java.util.Optional;

/**
 * Parser resiliente: tenta múltiplos seletores CSS e fallback com JSoup.
 * Para campos de preço, extrai texto e limpa formatação (R$).
 */
public class ResilientParser implements ParserPort {

    private static final Logger log = LoggerFactory.getLogger(ResilientParser.class);

    // Listas de seletores por campo (prioridade decrescente)
    private static final List<String> TITLE_SELECTORS = Arrays.asList(
            "[data-testid='product-title']",
            ".product-title",
            "h1[data-testid='product-name']",
            "h1"
    );
    private static final List<String> NORMAL_PRICE_SELECTORS = Arrays.asList(
            "[data-testid='price-original']",
            ".original-price",
            ".price-original",
            "[data-testid='price-value']:not(.discount)"
    );
    private static final List<String> DISCOUNT_PRICE_SELECTORS = Arrays.asList(
            "[data-testid='price-discount']",
            ".discount-price",
            "[data-testid='price-value']"
    );
    private static final List<String> IMAGE_SELECTORS = Arrays.asList(
            "[data-testid='product-image'] img",
            ".product-image img",
            "img[alt*='produto']",
            "img:first-of-type"
    );

    @Override
    public ProductData parse(Page page, URL originalUrl) {
        String title = extractWithFallback(page, TITLE_SELECTORS);
        String normalPrice = extractPrice(page, NORMAL_PRICE_SELECTORS);
        String discountPrice = extractPrice(page, DISCOUNT_PRICE_SELECTORS);
        String imageUrl = extractAttribute(page, IMAGE_SELECTORS, "src");

        // Se não encontrou nenhum preço, tenta extrair do HTML via JSoup (fallback)
        if ((normalPrice == null || normalPrice.isEmpty()) && (discountPrice == null || discountPrice.isEmpty())) {
            String html = page.content();
            Document doc = Jsoup.parse(html);
            normalPrice = extractPriceFromJsoup(doc, NORMAL_PRICE_SELECTORS);
            discountPrice = extractPriceFromJsoup(doc, DISCOUNT_PRICE_SELECTORS);
            if (title == null) title = extractTextFromJsoup(doc, TITLE_SELECTORS);
            if (imageUrl == null) imageUrl = extractAttributeFromJsoup(doc, IMAGE_SELECTORS, "src");
        }

        // Constrói ProductData com sucesso (campos podem ser nulos)
        try {
            return ProductData.success(title, normalPrice, discountPrice, originalUrl,
                    imageUrl != null ? new URL(imageUrl) : null);
        } catch (Exception e) {
            log.warn("Erro ao criar URL da imagem: {}", e.getMessage());
            return ProductData.success(title, normalPrice, discountPrice, originalUrl, null);
        }
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
            if (element != null && !element.text().isBlank()) return element.text().trim();
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