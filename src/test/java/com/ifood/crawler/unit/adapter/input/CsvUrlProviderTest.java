package com.ifood.crawler.unit.adapter.input;

import com.ifood.crawler.adapter.input.CsvUrlProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvUrlProviderTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadUrlsFromCsvWithoutHeader() throws IOException {
        Path csvPath = tempDir.resolve("urls.csv");
        Files.write(csvPath, List.of(
                "https://www.ifood.com.br/produto/1",
                "https://www.ifood.com.br/produto/2",
                "https://www.ifood.com.br/produto/3"
        ));

        CsvUrlProvider provider = new CsvUrlProvider(csvPath);
        
        List<String> urls = provider.urls().toList();
        
        assertThat(urls).hasSize(3);
        assertThat(urls).containsExactly(
                "https://www.ifood.com.br/produto/1",
                "https://www.ifood.com.br/produto/2",
                "https://www.ifood.com.br/produto/3"
        );
        assertThat(provider.totalUrls()).isEqualTo(3);
    }

    @Test
    void shouldReadUrlsFromCsvWithHeader() throws IOException {
        Path csvPath = tempDir.resolve("urls-with-header.csv");
        Files.write(csvPath, List.of(
                "id,url,description",
                "1,https://www.ifood.com.br/produto/1,Produto 1",
                "2,https://www.ifood.com.br/produto/2,Produto 2",
                "3,https://www.ifood.com.br/produto/3,Produto 3"
        ));

        CsvUrlProvider provider = new CsvUrlProvider(csvPath);
        
        List<String> urls = provider.urls().toList();
        
        assertThat(urls).hasSize(3);
        assertThat(urls).containsExactly(
                "https://www.ifood.com.br/produto/1",
                "https://www.ifood.com.br/produto/2",
                "https://www.ifood.com.br/produto/3"
        );
        assertThat(provider.totalUrls()).isEqualTo(3);
    }

    @Test
    void shouldHandleEmptyFile() throws IOException {
        Path csvPath = tempDir.resolve("empty.csv");
        Files.write(csvPath, List.of());

        CsvUrlProvider provider = new CsvUrlProvider(csvPath);
        
        List<String> urls = provider.urls().toList();
        
        assertThat(urls).isEmpty();
        assertThat(provider.totalUrls()).isZero();
    }

    @Test
    void shouldSkipBlankLines() throws IOException {
        Path csvPath = tempDir.resolve("urls-with-blanks.csv");
        Files.write(csvPath, List.of(
                "https://www.ifood.com.br/produto/1",
                "",
                "https://www.ifood.com.br/produto/2",
                "   ",
                "https://www.ifood.com.br/produto/3"
        ));

        CsvUrlProvider provider = new CsvUrlProvider(csvPath);
        
        List<String> urls = provider.urls().toList();
        
        assertThat(urls).hasSize(3);
        assertThat(urls).containsExactly(
                "https://www.ifood.com.br/produto/1",
                "https://www.ifood.com.br/produto/2",
                "https://www.ifood.com.br/produto/3"
        );
    }
}