package test.java.com.ifood.crawler.integration;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.adapter.output.SqlitePersistence;
import com.ifood.crawler.core.model.CrawlResult;
import com.ifood.crawler.core.model.CrawlStatus;
import com.ifood.crawler.core.model.ProductData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SqlitePersistenceIntegrationTest {

    @TempDir
    Path tempDir;

    private SqlitePersistence persistence;
    private AppConfig config;

    @BeforeEach
    void setUp() throws Exception {
        Path dbPath = tempDir.resolve("test.db");
        config = new AppConfig();
        persistence = new SqlitePersistence(config);
    }

    @Test
    void shouldSaveAndRetrieveResults() throws Exception {
        URL url = new URL("https://www.ifood.com.br/produto/test");
        ProductData data = ProductData.success(
                "Test Product",
                "10.00",
                "8.00",
                url,
                new URL("https://images.ifood.com.br/test.jpg")
        );
        
        CrawlResult result = new CrawlResult(
                url.toString(),
                data,
                1,
                1000,
                Instant.now(),
                false
        );

        persistence.saveResult(result);

        List<CrawlResult> results = persistence.getAllResults();
        assertThat(results).hasSize(1);
        
        CrawlResult saved = results.get(0);
        assertThat(saved.url()).isEqualTo(url.toString());
        assertThat(saved.productData().title()).isEqualTo("Test Product");
        assertThat(saved.productData().normalPrice()).isEqualTo("10.00");
        assertThat(saved.productData().status()).isEqualTo(CrawlStatus.SUCCESS);
    }

    @Test
    void shouldCheckIfUrlIsAlreadyProcessed() throws Exception {
        URL url = new URL("https://www.ifood.com.br/produto/processed");
        ProductData data = ProductData.success(
                "Processed Product",
                null, null, url, null
        );
        
        CrawlResult result = new CrawlResult(
                url.toString(),
                data,
                1,
                500,
                Instant.now(),
                false
        );

        persistence.saveResult(result);

        assertThat(persistence.isAlreadyProcessed(url.toString())).isTrue();
        assertThat(persistence.isAlreadyProcessed("https://www.ifood.com.br/produto/not-processed")).isFalse();
    }

    @Test
    void shouldExportToJsonAndCsv() throws Exception {
        //criar alguns resultados
        URL url1 = new URL("https://www.ifood.com.br/produto/1");
        ProductData data1 = ProductData.success("Produto 1", "10.00", null, url1, null);
        CrawlResult result1 = new CrawlResult(url1.toString(), data1, 1, 500, Instant.now(), false);

        URL url2 = new URL("https://www.ifood.com.br/produto/2");
        ProductData data2 = ProductData.error(url2, "Timeout");
        CrawlResult result2 = new CrawlResult(url2.toString(), data2, 3, 5000, Instant.now(), true);

        persistence.saveResult(result1);
        persistence.saveResult(result2);

        //exportar
        List<CrawlResult> results = persistence.getAllResults();
        persistence.exportFinalResults(results);

        //verificar arquivos
        Path outputDir = tempDir.resolve("output");
        assertThat(outputDir.resolve("results.json")).exists();
        assertThat(outputDir.resolve("results.csv")).exists();
    }
}