package com.ifood.crawler.integration;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.adapter.output.PlaywrightCrawler;
import com.ifood.crawler.core.port.output.CrawlerPort;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaywrightCrawlerIntegrationTest {

    private CrawlerPort crawler;

    @BeforeEach
    void setUp() {
        AppConfig config = new AppConfig();
        crawler = new PlaywrightCrawler(config);
    }

    @AfterEach
    void tearDown() {
        crawler.close();
    }

    @Test
    void shouldFetchGooglePage() throws Exception {
        Optional<Page> pageOpt = crawler.fetchPage("https://www.google.com");
        
        assertThat(pageOpt).isPresent();
        
        try (Page page = pageOpt.get()) {
            assertThat(page.title()).contains("Google");
        }
    }

    @Test
    void shouldThrowExceptionForInvalidUrl() {
        assertThatThrownBy(() -> 
            crawler.fetchPage("https://www.ifood.com.br/pagina-inexistente-123456789")
        ).isInstanceOf(Exception.class);
    }

    @Test
    void shouldHandleTimeout() {
        assertThatThrownBy(() -> 
            crawler.fetchPage("https://www.google.com")
        ).isInstanceOf(Exception.class);
    }
}