package test.java.com.ifood.crawler.unit.core.model;

import com.ifood.crawler.core.model.CrawlStatus;
import com.ifood.crawler.core.model.ProductData;
import org.junit.jupiter.api.Test;

import java.net.URL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductDataTest {

    @Test
    void shouldCreateSuccessProductData() throws Exception {
        URL url = new URL("https://www.ifood.com.br/produto/123");
        ProductData data = ProductData.success(
                "X-Burger",
                "R$ 39,90",
                "R$ 29,90",
                url,
                new URL("https://images.ifood.com.br/123.jpg")
        );

        assertThat(data.title()).isEqualTo("X-Burger");
        assertThat(data.normalPrice()).isEqualTo("R$ 39,90");
        assertThat(data.discountPrice()).isEqualTo("R$ 29,90");
        assertThat(data.productUrl()).isEqualTo(url);
        assertThat(data.status()).isEqualTo(CrawlStatus.SUCCESS);
        assertThat(data.errorMessage()).isNull();
    }

    @Test
    void shouldCreateErrorProductData() throws Exception {
        URL url = new URL("https://www.ifood.com.br/produto/456");
        ProductData data = ProductData.error(url, "Produto indisponível");

        assertThat(data.title()).isNull();
        assertThat(data.normalPrice()).isNull();
        assertThat(data.discountPrice()).isNull();
        assertThat(data.imageUrl()).isNull();
        assertThat(data.status()).isEqualTo(CrawlStatus.ERROR);
        assertThat(data.errorMessage()).isEqualTo("Produto indisponível");
    }

    @Test
    void shouldThrowExceptionWhenErrorWithoutMessage() throws Exception {
        URL url = new URL("https://www.ifood.com.br/produto/789");
        
        assertThatThrownBy(() -> new ProductData(
                null, null, null, url, null, CrawlStatus.ERROR, null
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Status ERROR requer mensagem de erro não vazia");
    }

    @Test
    void shouldThrowExceptionWhenProductUrlIsNull() {
        assertThatThrownBy(() -> new ProductData(
                "Title", "10.00", "8.00", null, null, CrawlStatus.SUCCESS, null
        )).isInstanceOf(NullPointerException.class);
    }
}