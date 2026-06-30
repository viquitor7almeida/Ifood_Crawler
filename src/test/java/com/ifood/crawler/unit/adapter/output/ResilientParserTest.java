package com.ifood.crawler.unit.adapter.output;

import com.ifood.crawler.adapter.output.ResilientParser;
import com.ifood.crawler.core.model.CrawlStatus;
import com.ifood.crawler.core.model.ProductData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;

class ResilientParserTest {

    private ResilientParser parser;

    @BeforeEach
    void setUp() {
        parser = new ResilientParser();
    }

    @Test
    void shouldParseAllFieldsFromHtml() throws Exception {
        String html = """
                <html>
                <body>
                    <h1>X-Burger Combo</h1>
                    <div data-testid="price-original">R$ 39,90</div>
                    <div data-testid="discount-price">R$ 29,90</div>
                    <meta property="og:image" content="https://images.ifood.com.br/combo.jpg" />
                </body>
                </html>
                """;

        URL url = new URL("https://www.ifood.com.br/produto/combo");
        ProductData result = parser.parse(html, url);

        assertThat(result.title()).isEqualTo("X-Burger Combo");
        assertThat(result.normalPrice()).isEqualTo("R$ 39,90");
        assertThat(result.discountPrice()).isEqualTo("R$ 29,90");
        assertThat(result.productUrl()).isEqualTo(url);
        assertThat(result.status()).isEqualTo(CrawlStatus.SUCCESS);
    }

    @Test
    void shouldHandleMissingFieldsGracefully() throws Exception {
        String html = """
                <html>
                    <body>
                        <h1>Produto Sem Preco</h1>
                        <img src="https://images.ifood.com.br/default.jpg" />
                    </body>
                </html>
                """;

        URL url = new URL("https://www.ifood.com.br/produto/sem-preco");
        ProductData result = parser.parse(html, url);

        assertThat(result.title()).isEqualTo("Produto Sem Preco");
        assertThat(result.status()).isEqualTo(CrawlStatus.SUCCESS);
    }

    @Test
    void shouldExtractFromDataTestId() throws Exception {
        String html = """
                <html>
                <body>
                    <div data-testid="product-title">Produto Test ID</div>
                    <div data-testid="price-original">R$ 99,90</div>
                    <meta property="og:image" content="https://images.ifood.com.br/testid.jpg" />
                </body>
                </html>
                """;

        URL url = new URL("https://www.ifood.com.br/produto/testid");
        ProductData result = parser.parse(html, url);

        assertThat(result.title()).isEqualTo("Produto Test ID");
        assertThat(result.normalPrice()).isEqualTo("R$ 99,90");
    }

    @Test
    void shouldExtractFromMetaTags() throws Exception {
        String html = """
                <html>
                <head>
                    <meta property="og:title" content="Produto Meta" />
                    <meta property="product:price:amount" content="79.90" />
                    <meta property="og:image" content="https://images.ifood.com.br/meta.jpg" />
                </head>
                <body></body>
                </html>
                """;

        URL url = new URL("https://www.ifood.com.br/produto/meta");
        ProductData result = parser.parse(html, url);

        assertThat(result.title()).isEqualTo("Produto Meta");
        assertThat(result.normalPrice()).isEqualTo("R$ 79,90");
    }

    @Test
    void shouldExtractFromJsonLd() throws Exception {
        String html = """
                <html>
                <head>
                    <script type="application/ld+json">
                    {
                        "@type": "Product",
                        "name": "Produto JSON-LD",
                        "offers": {
                            "price": "49.90"
                        },
                        "image": "https://images.ifood.com.br/jsonld.jpg"
                    }
                    </script>
                </head>
                <body></body>
                </html>
                """;

        URL url = new URL("https://www.ifood.com.br/produto/jsonld");
        ProductData result = parser.parse(html, url);

        assertThat(result.title()).isEqualTo("Produto JSON-LD");
        assertThat(result.normalPrice()).isEqualTo("R$ 49,90");
    }

    @Test
    void shouldExtractFromJsonLdWithOffersArray() throws Exception {
        String html = """
                <html>
                <head>
                    <script type="application/ld+json">
                    {
                        "@type": "Product",
                        "name": "Produto Oferta",
                        "offers": [
                            {
                                "@type": "Offer",
                                "price": "59.90",
                                "priceType": "https://schema.org/ListPrice"
                            },
                            {
                                "@type": "Offer",
                                "price": "39.90",
                                "priceType": "https://schema.org/SalePrice"
                            }
                        ],
                        "image": "https://images.ifood.com.br/oferta.jpg"
                    }
                    </script>
                </head>
                <body></body>
                </html>
                """;

        URL url = new URL("https://www.ifood.com.br/produto/oferta");
        ProductData result = parser.parse(html, url);

        assertThat(result.title()).isEqualTo("Produto Oferta");
        assertThat(result.normalPrice()).isEqualTo("R$ 59,90");
        assertThat(result.discountPrice()).isEqualTo("R$ 39,90");
    }
}
