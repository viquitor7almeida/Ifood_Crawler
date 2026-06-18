package main.java.com.ifood.crawler.adapter.output;

import com.ifood.crawler.adapter.config.AppConfig;
import com.ifood.crawler.core.port.output.CrawlerPort;
import com.microsoft.playwright.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Implementação do CrawlerPort usando Playwright.
 * Reutiliza uma única instância do browser e context para todas as URLs.
 * Cada página é criada e fechada individualmente para evitar vazamento de memória.
 */
public class PlaywrightCrawler implements CrawlerPort {

    private static final Logger log = LoggerFactory.getLogger(PlaywrightCrawler.class);
    private final Playwright playwright;
    private final Browser browser;
    private final BrowserContext context;
    private final AppConfig config;

    public PlaywrightCrawler(AppConfig config) {
        this.config = config;
        this.playwright = Playwright.create();
        // Configuração headless e user-agent
        BrowserType.LaunchOptions launchOpts = new BrowserType.LaunchOptions().setHeadless(true);
        this.browser = playwright.chromium().launch(launchOpts);
        // Contexto com user-agent e viewport fixo para evitar fingerprint
        this.context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent(config.getUserAgent())
                .setViewportSize(1280, 1024));
    }

    @Override
    public Optional<Page> fetchPage(String url) throws Exception {
        Page page = null;
        try {
            page = context.newPage();
            // Navega com timeout e aguarda network idle para garantir carregamento JS
            page.navigate(url, new Page.NavigateOptions()
                    .setTimeout(config.getNavigationTimeout().toMillis()));
            page.waitForLoadState(LoadState.NETWORKIDLE);
            // Aguarda seletor comum de produto para garantir que o conteúdo dinâmico foi renderizado
            // Se falhar, não lança exceção, apenas loga e prossegue (pode ser página inválida)
            try {
                page.waitForSelector(".product-title, [data-testid='product-title'], h1",
                        new Page.WaitForSelectorOptions().setTimeout(config.getSelectorTimeout().toMillis()));
            } catch (TimeoutException e) {
                log.warn("Timeout aguardando seletor de produto para URL: {}", url);
            }
            return Optional.of(page);
        } catch (Exception e) {
            if (page != null) page.close();
            log.error("Falha ao acessar URL {}: {}", url, e.getMessage());
            // Relançar como exceção controlada para retry
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