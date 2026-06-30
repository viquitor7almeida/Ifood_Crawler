package com.ifood.crawler.adapter.output;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ifood.crawler.core.model.ProductData;
import com.ifood.crawler.core.port.output.ParserPort;
import com.ifood.crawler.infra.logging.StructuredLogger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResilientParser implements ParserPort {

    private static final StructuredLogger log = StructuredLogger.getLogger(ResilientParser.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Pattern BRL_PRICE = Pattern.compile(
            "R\\$\\s*([\\d.,]+)");

    private static final Set<String> TITLE_SELECTORS = new LinkedHashSet<>(List.of(
            "[data-testid='product-title']",
            "[data-testid='item-name']",
            "[data-testid='dish-name']",
            "[data-testid='product-name']"
    ));

    private static final Set<String> NORMAL_PRICE_SELECTORS = new LinkedHashSet<>(List.of(
            "[data-testid='product-price']",
            "[data-testid='price-value']",
            "[data-testid='price']",
            "[data-testid='price-original']",
            "[data-testid='original-price']",
            "[data-testid='full-price']"
    ));

    private static final Set<String> DISCOUNT_PRICE_SELECTORS = new LinkedHashSet<>(List.of(
            "[data-testid='discount-price']",
            "[data-testid='price-discount']",
            "[data-testid='promo-price']",
            "[data-testid='final-price']",
            "[data-testid='current-price']"
    ));

    @Override
    public ProductData parse(String html, URL originalUrl) {
        Document doc = Jsoup.parse(html, originalUrl.toString());

        ProductData result;

        result = tryJsonLd(doc, originalUrl);
        if (result != null) {
            log.info("JSON-LD parsed: {}", originalUrl);
            return result;
        }

        result = tryDataTestId(doc, originalUrl);
        if (result != null) {
            log.info("data-testid parsed: {}", originalUrl);
            return result;
        }

        result = tryMetaTags(doc, originalUrl);
        if (result != null) {
            log.info("meta tags parsed: {}", originalUrl);
            return result;
        }

        result = tryNextData(html, doc, originalUrl);
        if (result != null) {
            log.info("__NEXT_DATA__ parsed: {}", originalUrl);
            return result;
        }

        result = tryCssFallback(doc, originalUrl);
        if (result != null) {
            log.info("CSS fallback parsed: {}", originalUrl);
            return result;
        }

        log.warn("Parse failed for all strategies: {}", originalUrl);
        saveDebugHtml(html, originalUrl);
        debugNextData(doc, originalUrl);
        return ProductData.error(originalUrl, "Parse falhou em todas as estrategias");
    }

    private void logNextDataStructure(Document doc, URL url) {
        try {
            Element nd = doc.selectFirst("script#__NEXT_DATA__");
            if (nd == null || nd.data() == null) return;
            String raw = nd.data();
            if (raw.length() > 5000) raw = raw.substring(0, 5000) + "\n... [truncated]";
            log.info("__NEXT_DATA__ ({})", url);
            log.info(raw);
        } catch (Exception e) {
            log.debug("NEXT_DATA log error: {}", e.getMessage());
        }
    }

    // ── JSON-LD ──────────────────────────────────────────────

    private ProductData tryJsonLd(Document doc, URL originalUrl) {
        for (Element script : doc.select("script")) {
            String type = script.attr("type");
            if ("application/ld+json".equals(type)) {
                ProductData p = parseJsonLdScript(script, originalUrl);
                if (p != null) return p;
            }
        }
        return null;
    }

    private ProductData parseJsonLdScript(Element script, URL originalUrl) {
        try {
            String raw = script.html();
            if (raw == null || raw.isBlank()) raw = script.data();
            if (raw == null || raw.isBlank()) return null;
            JsonNode data = MAPPER.readTree(raw);
            ProductData p = extractJsonLdProduct(data, originalUrl);
            if (p != null) return p;
            if (data.isArray()) {
                for (JsonNode item : data) {
                    p = extractJsonLdProduct(item, originalUrl);
                    if (p != null) return p;
                }
            }
        } catch (Exception e) {
            log.debug("JSON-LD parse error: {}", e.getMessage());
        }
        return null;
    }

    private ProductData extractJsonLdProduct(JsonNode data, URL originalUrl) {
        String type = data.path("@type").asText("");
        if (!"Product".equals(type) && !"ItemPage".equals(type)) return null;

        String title = data.path("name").asText(null);
        if (title == null) title = data.path("headline").asText(null);
        if (title == null) return null;

        Double normalPrice = null;
        Double discountPrice = null;

        JsonNode offers = data.path("offers");
        if (offers.isObject()) {
            List<Double> prices = extractPricesFromOffer(offers);
            if (prices.get(0) != null) normalPrice = prices.get(0);
            if (prices.get(1) != null) discountPrice = prices.get(1);
        } else if (offers.isArray()) {
            for (JsonNode offer : offers) {
                List<Double> prices = extractPricesFromOffer(offer);
                if (prices.get(1) != null) discountPrice = prices.get(1);
                if (prices.get(0) != null && discountPrice == null) normalPrice = prices.get(0);
                if (prices.get(0) != null && discountPrice != null && normalPrice == null) {
                    normalPrice = prices.get(0);
                }
            }
        }

        if (normalPrice == null) {
            normalPrice = parsePrice(data.path("price").asText(null));
        }

        String image = extractImageUrl(data.path("image"));

        return ProductData.success(
                title,
                normalPrice != null ? formatBRL(normalPrice) : null,
                discountPrice != null ? formatBRL(discountPrice) : null,
                originalUrl,
                image != null ? toUrl(image) : null
        );
    }

    private List<Double> extractPricesFromOffer(JsonNode offer) {
        Double normal = null;
        Double discount = null;
        String priceType = offer.path("priceType").asText("");
        Double price = parsePrice(offer.path("price").asText(null));

        if ("https://schema.org/SalePrice".equals(priceType) || priceType.toLowerCase().contains("sale")) {
            discount = price;
        } else if ("https://schema.org/ListPrice".equals(priceType) || priceType.toLowerCase().contains("list")) {
            normal = price;
        } else {
            normal = price;
        }

        if (normal == null) {
            Double highPrice = parsePrice(offer.path("highPrice").asText(null));
            if (highPrice != null) normal = highPrice;
        }

        var result = new ArrayList<Double>();
        result.add(normal);
        result.add(discount);
        return result;
    }

    // ── data-testid ──────────────────────────────────────────

    private ProductData tryDataTestId(Document doc, URL originalUrl) {
        logNextDataStructure(doc, originalUrl);
        Element titleEl = selectFirst(doc, TITLE_SELECTORS);
        if (titleEl == null) return null;
        String title = titleEl.text().trim();
        if (title.isEmpty()) return null;

        Double normalPrice = null;
        Element priceEl = selectFirst(doc, NORMAL_PRICE_SELECTORS);
        if (priceEl != null) normalPrice = parsePrice(priceEl.text());

        Double discountPrice = null;
        Element discountEl = selectFirst(doc, DISCOUNT_PRICE_SELECTORS);
        if (discountEl != null) discountPrice = parsePrice(discountEl.text());

        String imageUrl = extractImageFromDoc(doc);

        return ProductData.success(
                title,
                normalPrice != null ? formatBRL(normalPrice) : null,
                discountPrice != null ? formatBRL(discountPrice) : null,
                originalUrl,
                imageUrl != null ? toUrl(imageUrl) : null
        );
    }

    // ── Meta tags ───────────────────────────────────────────

    private ProductData tryMetaTags(Document doc, URL originalUrl) {
        String title = null;
        for (String prop : List.of("og:title", "twitter:title", "product:name")) {
            Element el = doc.selectFirst("meta[property='" + prop + "']");
            if (el == null) el = doc.selectFirst("meta[name='" + prop + "']");
            if (el != null && el.hasAttr("content")) {
                title = el.attr("content");
                break;
            }
        }
        if (title == null) {
            Element titleTag = doc.selectFirst("title");
            if (titleTag != null) title = titleTag.text().trim();
        }
        if (title == null || title.isEmpty()) return null;

        Double normalPrice = null;
        for (String prop : List.of("product:price:amount", "og:price:amount")) {
            Element el = doc.selectFirst("meta[property='" + prop + "']");
            if (el != null && el.hasAttr("content")) {
                normalPrice = parsePrice(el.attr("content"));
                if (normalPrice != null) break;
            }
        }

        if (normalPrice == null) {
            Matcher m = BRL_PRICE.matcher(doc.html());
            while (m.find()) {
                Double p = parsePrice(m.group(0));
                if (p != null && p > 0) {
                    normalPrice = p;
                    break;
                }
            }
        }

        Double discountPrice = null;
        for (String prop : List.of("product:sale_price:amount", "og:sale_price:amount")) {
            Element el = doc.selectFirst("meta[property='" + prop + "']");
            if (el != null && el.hasAttr("content")) {
                discountPrice = parsePrice(el.attr("content"));
                if (discountPrice != null) break;
            }
        }

        String imageUrl = extractMetaImage(doc);

        if (normalPrice == null && title != null) {
            log.info("meta tags parsed (title only, no price): {}", originalUrl);
        }

        return ProductData.success(
                title,
                normalPrice != null ? formatBRL(normalPrice) : null,
                discountPrice != null ? formatBRL(discountPrice) : null,
                originalUrl,
                imageUrl != null ? toUrl(imageUrl) : null
        );
    }

    // ── __NEXT_DATA__ ───────────────────────────────────────

    private ProductData tryNextData(String html, Document doc, URL originalUrl) {
        Element nd = doc.selectFirst("script#__NEXT_DATA__");
        if (nd == null || nd.data() == null) return null;

        JsonNode ndata;
        try {
            ndata = MAPPER.readTree(nd.data());
        } catch (Exception e) {
            log.debug("__NEXT_DATA__ parse error: {}", e.getMessage());
            return null;
        }

        JsonNode pageProps = ndata.path("props").path("pageProps");
        if (pageProps.isMissingNode()) pageProps = ndata.path("props").path("initialProps").path("pageProps");

        // Search paths for iFood item data
        ProductData result = tryNextDataPaths(ndata, pageProps, html, doc, originalUrl);
        if (result != null) {
            log.info("__NEXT_DATA__ matched specific path: {}", originalUrl);
            return result;
        }

        // Deep search: find any node with name + price
        result = deepSearchItem(ndata, html, doc, originalUrl);
        if (result != null) {
            log.info("__NEXT_DATA__ deep search matched: {}", originalUrl);
            return result;
        }

        log.info("__NEXT_DATA__ presente mas sem dados de produto para: {}", originalUrl);
        debugNextData(doc, originalUrl);
        return null;
    }

    private ProductData tryNextDataPaths(JsonNode ndata, JsonNode pageProps, String html, Document doc, URL url) {
        // known iFood paths
        List<String> paths = List.of(
            "productData",
            "item",
            "product",
            "menuItem",
            "cardapioItem",
            "menuItemSelected",
            "selectedItem"
        );
        for (String p : paths) {
            JsonNode node = pageProps.path(p);
            if (node.isMissingNode() || node.isNull()) continue;
            ProductData pd = extractNextDataItem(node, html, doc, url);
            if (pd != null) return pd;
        }

        // Search merchant items for the matching item
        JsonNode merchant = pageProps.path("merchant");
        if (merchant.isMissingNode() || merchant.isNull()) {
            merchant = pageProps.path("initialMerchantData");
        }
        if (!merchant.isMissingNode() && !merchant.isNull()) {
            ItemTitle result = findItemInMerchant(merchant, html, doc, url);
            if (result != null) {
                return ProductData.success(
                        result.title,
                        result.normalPrice != null ? formatBRL(result.normalPrice) : null,
                        null,
                        url,
                        result.imageUrl != null ? toUrl(result.imageUrl) : null
                );
            }
        }

        return null;
    }

    private record ItemTitle(String title, Double normalPrice, String imageUrl) {}

    private ItemTitle findItemInMerchant(JsonNode merchant, String html, Document doc, URL url) {
        String merchantName = merchant.path("description").asText(null);
        if (merchantName == null) merchantName = merchant.path("name").asText(null);
        if (merchantName == null) return null;

        String logo = merchant.path("logoUrl").asText(null);
        if (logo == null || logo.isBlank()) logo = extractMetaImage(doc);
        if (logo != null && !logo.startsWith("http")) {
            logo = "https://static.ifood-static.com.br/image/upload/t_high/" + logo;
        }

        // Search items array in merchant data
        for (String itemsPath : List.of("items", "menu", "categories", "category", "products")) {
            JsonNode itemsNode = merchant.path(itemsPath);
            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    ItemTitle found = extractItemFromNode(item);
                    if (found != null) return found;
                    // might be nested (categories > items)
                    if (item.has("items") && item.get("items").isArray()) {
                        for (JsonNode sub : item.get("items")) {
                            ItemTitle subFound = extractItemFromNode(sub);
                            if (subFound != null) return subFound;
                        }
                    }
                }
            }
        }
        return null;
    }

    private ItemTitle extractItemFromNode(JsonNode node) {
        String name = node.path("name").asText(null);
        if (name == null || name.isBlank()) return null;

        Double price = null;
        JsonNode pn = node.path("price");
        if (!pn.isMissingNode()) {
            if (pn.isNumber()) price = pn.asDouble();
            else if (pn.isTextual()) price = parsePrice(pn.asText());
        }
        if (price == null) {
            pn = node.path("originalPrice");
            if (!pn.isMissingNode() && pn.isNumber()) price = pn.asDouble();
        }
        if (price == null) return null;

        String image = node.path("image").asText(null);
        if (image == null) image = node.path("logoUrl").asText(null);

        return new ItemTitle(name, price, image);
    }

    private ProductData extractNextDataItem(JsonNode node, String html, Document doc, URL url) {
        String title = node.path("description").asText(null);
        if (title == null) title = node.path("name").asText(null);
        if (title == null) return null;

        Double normalPrice = null;
        // Try structured price field
        JsonNode priceNode = node.path("price");
        if (!priceNode.isMissingNode() && !priceNode.isNull()) {
            if (priceNode.isTextual()) {
                normalPrice = parsePrice(priceNode.asText());
            } else if (priceNode.isNumber()) {
                normalPrice = priceNode.asDouble();
            }
        }
        // Try price fields inside menu/pricing
        if (normalPrice == null) {
            JsonNode pricing = node.path("pricing");
            if (!pricing.isMissingNode()) {
                normalPrice = parsePrice(pricing.path("originalPrice").asText(null));
                if (normalPrice == null) normalPrice = parsePrice(pricing.path("price").asText(null));
                if (normalPrice == null && pricing.isNumber()) normalPrice = pricing.asDouble();
            }
        }
        // BRL regex fallback
        if (normalPrice == null) {
            Matcher m = BRL_PRICE.matcher(doc.html());
            while (m.find()) {
                Double p = parsePrice(m.group(0));
                if (p != null && p > 0) {
                    normalPrice = p;
                    break;
                }
            }
        }

        String imageUrl = node.path("logoUrl").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) imageUrl = node.path("image").asText(null);
        if (imageUrl == null || imageUrl.isBlank()) imageUrl = extractMetaImage(doc);
        if (imageUrl != null && !imageUrl.startsWith("http")) {
            imageUrl = "https://static.ifood-static.com.br/image/upload/t_high/" + imageUrl;
        }

        return ProductData.success(
                title,
                normalPrice != null ? formatBRL(normalPrice) : null,
                null,
                url,
                imageUrl != null ? toUrl(imageUrl) : null
        );
    }

    private ProductData deepSearchItem(JsonNode root, String html, Document doc, URL url) {
        if (root == null || root.isNull()) return null;
        return deepSearchItem(root, html, doc, url, 0);
    }

    private ProductData deepSearchItem(JsonNode node, String html, Document doc, URL url, int depth) {
        if (depth > 10 || node == null || node.isNull()) return null;

        if (node.isObject()) {
            String title = node.path("name").asText(null);
            if (title == null) title = node.path("description").asText(null);
            if (title != null && !title.isBlank()) {
                // Check for numeric price
                Double price = null;
                JsonNode pn = node.path("price");
                if (!pn.isMissingNode() && pn.isNumber()) price = pn.asDouble();
                if (price == null) {
                    JsonNode pn2 = node.path("originalPrice");
                    if (!pn2.isMissingNode() && pn2.isNumber()) price = pn2.asDouble();
                }
                if (price != null) {
                    String imageUrl = node.path("image").asText(null);
                    if (imageUrl == null) imageUrl = node.path("logoUrl").asText(null);
                    if (imageUrl == null || imageUrl.isBlank()) imageUrl = extractMetaImage(doc);
                    return ProductData.success(
                            title,
                            formatBRL(price),
                            null,
                            url,
                            imageUrl != null ? toUrl(imageUrl) : null
                    );
                }
            }

            for (JsonNode child : node) {
                ProductData r = deepSearchItem(child, html, doc, url, depth + 1);
                if (r != null) return r;
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                ProductData r = deepSearchItem(child, html, doc, url, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    private ProductData tryRegexFallback(String html, Document doc, URL url) {
        // Try meta title + regex price
        String title = null;
        for (String prop : List.of("og:title", "twitter:title")) {
            Element el = doc.selectFirst("meta[property='" + prop + "']");
            if (el == null) el = doc.selectFirst("meta[name='" + prop + "']");
            if (el != null && el.hasAttr("content")) {
                title = el.attr("content");
                break;
            }
        }
        if (title == null) {
            Element titleTag = doc.selectFirst("title");
            if (titleTag != null) title = titleTag.text().trim();
        }
        if (title == null || title.isBlank()) return null;

        Double normalPrice = null;
        Matcher m = BRL_PRICE.matcher(doc.html());
        if (m.find()) normalPrice = parsePrice(m.group(0));

        String imageUrl = extractMetaImage(doc);

        return ProductData.success(
                title,
                normalPrice != null ? formatBRL(normalPrice) : null,
                null,
                url,
                imageUrl != null ? toUrl(imageUrl) : null
        );
    }

    // ── CSS fallback ────────────────────────────────────────

    private ProductData tryCssFallback(Document doc, URL originalUrl) {
        Element titleEl = selectFirst(doc, new LinkedHashSet<>(List.of(
                ".product-detail__description",
                ".product-title",
                ".item-title",
                ".name",
                "h1", "h2"
        )));
        if (titleEl == null) return null;
        String title = titleEl.text().trim();
        if (title.isEmpty() || title.length() < 3) return null;

        Double normalPrice = null;
        Element priceEl = selectFirst(doc, new LinkedHashSet<>(List.of(
                ".product-card__price--original",
                ".price-tag",
                ".product-price",
                ".price",
                "[class*=price]"
        )));
        if (priceEl != null) normalPrice = parsePrice(priceEl.text());

        if (normalPrice == null) {
            Matcher m = BRL_PRICE.matcher(doc.html());
            while (m.find()) {
                Double p = parsePrice(m.group(0));
                if (p != null && p > 0) {
                    normalPrice = p;
                    break;
                }
            }
        }

        Double discountPrice = null;
        Element discountEl = doc.selectFirst(".product-card__price--discount");
        if (discountEl != null) {
            String ownText = discountEl.ownText();
            if (ownText != null && !ownText.isBlank()) {
                discountPrice = parsePrice(ownText);
            } else {
                discountPrice = parsePrice(discountEl.text());
            }
        }

        if (discountPrice == null && normalPrice != null) {
            Element pctEl = doc.selectFirst(".product-card__price--discount-percentage");
            if (pctEl != null) {
                String pctText = pctEl.text().replace("%", "").replace("-", "").replace("+", "").trim();
                try {
                    double pct = Double.parseDouble(pctText);
                    discountPrice = Math.round(normalPrice * (1 - pct / 100) * 100.0) / 100.0;
                } catch (NumberFormatException ignored) {}
            }
        }

        if (discountPrice == null) {
            Element altDiscount = selectFirst(doc, new LinkedHashSet<>(List.of(
                    ".discount-price", ".promo-price", ".sale-price", "[class*=discount]"
            )));
            if (altDiscount != null) discountPrice = parsePrice(altDiscount.text());
        }

        String imageUrl = extractImageFromDoc(doc);

        return ProductData.success(
                title,
                normalPrice != null ? formatBRL(normalPrice) : null,
                discountPrice != null ? formatBRL(discountPrice) : null,
                originalUrl,
                imageUrl != null ? toUrl(imageUrl) : null
        );
    }

    // ── Debug ───────────────────────────────────────────────

    private void saveDebugHtml(String html, URL url) {
        try {
            String hash = Integer.toHexString(url.toString().hashCode());
            Path dir = Path.of("/tmp");
            Path file = dir.resolve("debug-html-" + hash + ".html");
            Files.writeString(file, html);
            log.info("HTML salvo para debug: {} ({} bytes)", file, html.length());
        } catch (Exception e) {
            log.debug("Nao foi possivel salvar HTML debug: {}", e.getMessage());
        }
    }

    private void debugNextData(Document doc, URL url) {
        Element nd = doc.selectFirst("script#__NEXT_DATA__");
        if (nd == null || nd.data() == null) {
            log.info("__NEXT_DATA__ nao encontrado em {}", url);
            return;
        }
        String raw = nd.data();
        int maxLen = Math.min(raw.length(), 3000);
        log.info("__NEXT_DATA__ encontrado ({} total chars). Primeiros {} chars:\n{}",
                raw.length(), maxLen, raw.substring(0, maxLen));
        try {
            JsonNode data = MAPPER.readTree(raw);
            List<String> topKeys = new ArrayList<>();
            data.fieldNames().forEachRemaining(topKeys::add);
            log.info("__NEXT_DATA__ top-level keys: {}", topKeys);
            JsonNode pp = data.path("props").path("pageProps");
            if (!pp.isMissingNode()) {
                List<String> ppKeys = new ArrayList<>();
                pp.fieldNames().forEachRemaining(ppKeys::add);
                log.info("__NEXT_DATA__.props.pageProps keys: {}", ppKeys);
            }
        } catch (Exception e) {
            log.debug("__NEXT_DATA__ parse error: {}", e.getMessage());
        }
    }

    // ── Helpers ─────────────────────────────────────────────

    private Element selectFirst(Document doc, Set<String> selectors) {
        for (String s : selectors) {
            Element el = doc.selectFirst(s);
            if (el != null) return el;
        }
        return null;
    }

    private String extractImageFromDoc(Document doc) {
        String[] imgSelectors = {
                "[data-testid='product-image'] img",
                "[data-testid='item-image'] img",
                "meta[property='og:image']",
                "meta[name='twitter:image']",
                "[class*='product-image'] img",
                "img[src*='ifood-static']",
                "img[src*='images.ifood']",
                "img[class*=product], img[class*=item]"
        };
        for (String sel : imgSelectors) {
            Element el = doc.selectFirst(sel);
            if (el != null) {
                String src = el.hasAttr("content") ? el.attr("content") : el.absUrl("src");
                if (src == null || src.isBlank()) src = el.attr("src");
                if (src != null && !src.isBlank()) return src;
            }
        }
        return null;
    }

    private String extractMetaImage(Document doc) {
        for (String prop : List.of("og:image", "twitter:image")) {
            Element el = doc.selectFirst("meta[property='" + prop + "']");
            if (el == null) el = doc.selectFirst("meta[name='" + prop + "']");
            if (el != null && el.hasAttr("content")) return el.attr("content");
        }
        return null;
    }

    private String extractImageUrl(JsonNode imageNode) {
        if (imageNode == null || imageNode.isNull()) return null;
        if (imageNode.isTextual()) return imageNode.asText();
        if (imageNode.isArray() && imageNode.size() > 0) {
            JsonNode first = imageNode.get(0);
            if (first.isTextual()) return first.asText();
            return first.path("url").asText(null);
        }
        if (imageNode.isObject()) {
            String u = imageNode.path("url").asText(null);
            if (u == null) u = imageNode.path("contentUrl").asText(null);
            return u;
        }
        return null;
    }

    private Double parsePrice(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replace("R$", "").replace(" ", "").trim();
        if (cleaned.contains(",") && cleaned.contains(".")) {
            if (cleaned.lastIndexOf(",") > cleaned.lastIndexOf(".")) {
                cleaned = cleaned.replace(".", "").replace(",", ".");
            } else {
                cleaned = cleaned.replace(",", "");
            }
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }
        Matcher m = Pattern.compile("(\\d+\\.?\\d*)").matcher(cleaned);
        if (m.find()) {
            try {
                return Double.parseDouble(m.group(1));
            } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    private String formatBRL(double value) {
        return String.format("R$ %.2f", value).replace(".", ",");
    }

    private URL toUrl(String url) {
        try { return new URL(url); } catch (Exception e) { return null; }
    }
}
