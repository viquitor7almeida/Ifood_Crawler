package com.ifood.crawler.unit.adapter.output;

import com.ifood.crawler.adapter.output.ResilientParser;
import com.ifood.crawler.core.model.ProductData;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilientParserTest {

    private ResilientParser parser;
    
    @Mock
    private Page mockPage;

    @BeforeEach
    void setUp() {
        parser = new ResilientParser();
    }

    @Test
    void shouldParseAllFieldsSuccessfully() throws Exception {
        // Mock do DOM
        when(mockPage.isVisible("[data-testid='product-title']")).thenReturn(true);
        when(mockPage.textContent("[data-testid='product-title']")).thenReturn("X-Burger Combo");
        
        when(mockPage.isVisible("[data-testid='price-original']")).thenReturn(true);
        when(mockPage.textContent("[data-testid='price-original']")).thenReturn("R$ 39,90");
        
        when(mockPage.isVisible("[data-testid='price-discount']")).thenReturn(true);
        when(mockPage.textContent("[data-testid='price-discount']")).thenReturn("R$ 29,90");
        
        when(mockPage.getAttribute("[data-testid='product-image'] img", "src"))
                .thenReturn("https://images.ifood.com.br/combo.jpg");

        URL url = new URL("https://www.ifood.com.br/produto/combo");
        ProductData result = parser.parse(mockPage, url);

        assertThat(result.title()).isEqualTo("X-Burger Combo");
        assertThat(result.normalPrice()).isEqualTo("39.90");
        assertThat(result.discountPrice()).isEqualTo("29.90");
        assertThat(result.productUrl()).isEqualTo(url);
        assertThat(result.imageUrl()).hasToString("https://images.ifood.com.br/combo.jpg");
        assertThat(result.status()).isEqualTo(com.ifood.crawler.core.model.CrawlStatus.SUCCESS);
    }

    @Test
    void shouldHandleMissingFieldsGracefully() throws Exception {
        // Mock com campos faltando
        when(mockPage.isVisible(anyString())).thenReturn(false);
        
        // Retorna null para todos os seletores
        when(mockPage.textContent(anyString())).thenReturn(null);
        when(mockPage.getAttribute(anyString(), anyString())).thenReturn(null);

        // Mock do HTML para fallback JSoup
        String html = """
                <html>
                    <body>
                        <h1>Produto Sem Preço</h1>
                        <img src="https://images.ifood.com.br/default.jpg" />
                    </body>
                </html>
                """;
        when(mockPage.content()).thenReturn(html);

        URL url = new URL("https://www.ifood.com.br/produto/sem-preco");
        ProductData result = parser.parse(mockPage, url);

        assertThat(result.title()).isEqualTo("Produto Sem Preço");
        assertThat(result.normalPrice()).isNull();
        assertThat(result.discountPrice()).isNull();
        assertThat(result.imageUrl()).hasToString("https://images.ifood.com.br/default.jpg");
        assertThat(result.status()).isEqualTo(com.ifood.crawler.core.model.CrawlStatus.SUCCESS);
        assertThat(result.errorMessage()).isNull();
    }

    @Test
    void shouldCleanPriceFormatting() throws Exception {
        when(mockPage.isVisible("[data-testid='price-original']")).thenReturn(true);
        when(mockPage.textContent("[data-testid='price-original']")).thenReturn("R$ 1.234,56");
        
        when(mockPage.isVisible("[data-testid='price-discount']")).thenReturn(true);
        when(mockPage.textContent("[data-testid='price-discount']")).thenReturn("R$ 999,90");

        URL url = new URL("https://www.ifood.com.br/produto/preco");
        ProductData result = parser.parse(mockPage, url);

        assertThat(result.normalPrice()).isEqualTo("1234.56");
        assertThat(result.discountPrice()).isEqualTo("999.90");
    }
}