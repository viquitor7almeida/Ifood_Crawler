package com.ifood.crawler.adapter.output;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.core.port.output.CrawlerPort;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class PlaywrightCrawler implements CrawlerPort {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightCrawler.class);
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final AppConfig config;

    public PlaywrightCrawler(AppConfig config) {
        this.config = config;
        this.playwright = Playwright.create();
        BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions().setHeadless(true);
        this.browser = playwright.chromium().launch(launchOpts);
        this.context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(config.getUserAgent())
                .setViewportSize(1280, 1024));
    }

    @Override
    public Optional<Page> fetchPage(String url) throws Exception {
        Page page = null;
        try {
            page = context.newPage();
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(config.getNavigationTimeout().toMillis()));
            
            page.waitForLoadState();
            
            // Espera o título - com try/catch genérico
            try {
                page.waitForSelector(".product-detail__description", 
                        new Page.WaitForSelectorOptions().setTimeout(5000));
            } catch (Exception e) {
                log.warn("Timeout aguardando título para URL: {}", url);
            }
            
            Thread.sleep(200);
            
            return Optional.of(page);
        } catch (Exception e) {
            if (page != null) page.close();
            log.error("Falha ao acessar URL {}: {}", url, e.getMessage());
            throw new RuntimeException("Falha no fetch: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        if (context != null) context.close();
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }
}