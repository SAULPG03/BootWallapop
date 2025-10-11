package com.saul.botwallapop.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.saul.botwallapop.config.ProductsConfig;
import com.saul.botwallapop.model.ProductConfig;
import com.saul.botwallapop.model.WallapopOffer;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class WallapopSearchService {

    private static final Logger log = LoggerFactory.getLogger(WallapopSearchService.class);

    private final ObjectMapper objectMapper;
    private final ProductsConfig productsConfig;
    private final List<ProductConfig> products = new ArrayList<>();
    private WebDriver driver;

    public WallapopSearchService(ProductsConfig productsConfig) {
        this.productsConfig = productsConfig;
        this.objectMapper = new ObjectMapper();
    }

    @PostConstruct
    public void init() {
        products.addAll(productsConfig.parseProducts());
        if (products.isEmpty()) {
            log.warn("⚠️ No hay productos configurados");
        } else {
            log.info("📦 Productos configurados:");
            for (ProductConfig p : products) {
                log.info("  ✅ {} (min: {}€)", p.getName(), p.getMinPrice());
            }
        }
        initWebDriver();
    }

    private void initWebDriver() {
        if (driver != null) return;

        ChromeOptions options = new ChromeOptions();
        // options.addArguments("--headless=new"); // modo headless opcional
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--lang=es-ES");
        options.addArguments("--start-maximized");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", List.of("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");

        driver = new ChromeDriver(options);
        log.info("🚀 WebDriver inicializado y listo para búsquedas");
    }

    public List<WallapopOffer> searchProduct(String query, double minPrice) {
        if (driver == null) initWebDriver();

        List<WallapopOffer> offers = new ArrayList<>();
        try {
            String searchUrl = "https://es.wallapop.com/app/search?keywords="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8);
            driver.get(searchUrl);

            // --- Aceptar cookies ---
            try {
                WebDriverWait waitCookies = new WebDriverWait(driver, Duration.ofSeconds(5));
                WebElement cookieBtn = waitCookies.until(
                    d -> d.findElement(By.id("onetrust-accept-btn-handler"))
                );
                cookieBtn.click();
                log.info("🍪 Cookies aceptadas automáticamente");
            } catch (Exception ignored) {}

            // --- Scroll infinito + "Cargar más" con Selenium ---
            int maxScrollAttempts = 30;
            int scrollAttempts = 0;
            int lastItemCount = 0;

            while (scrollAttempts < maxScrollAttempts) {
                // Scroll hasta el final
                ((JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1500 + (int)(Math.random() * 1000));

                // Pulsar "Cargar más" si aparece
                try {
                    List<WebElement> loadBtns = driver.findElements(By.cssSelector(
                        "div.search-page-results_SearchPageResults__loadMore__A_eRR walla-button"
                    ));
                    if (!loadBtns.isEmpty()) {
                        WebElement btn = loadBtns.get(0);
                        if (btn.isDisplayed() && btn.isEnabled()) {
                            btn.click(); // Selenium hace click real
                            log.info("➡️ Pulsado 'Cargar más' con Selenium");
                            Thread.sleep(1200 + (int)(Math.random() * 800));
                        }
                    }
                } catch (Exception ignored) {}

                // Contar items actuales
                List<WebElement> items = driver.findElements(By.cssSelector("a[href*='/item/']"));
                if (items.size() == lastItemCount) {
                    // Si no hay más items nuevos, salimos
                    break;
                }
                lastItemCount = items.size();
                scrollAttempts++;
            }

            // --- Extraer productos ---
            List<WebElement> items = driver.findElements(By.cssSelector("a[href*='/item/']"));
            for (WebElement el : items) {
                try {
                    String title = "";
                    try { title = el.findElement(By.cssSelector("h3")).getText(); } catch (Exception ignored) {}
                    String priceText = "";
                    double price = 0;
                    try {
                        priceText = el.findElement(By.cssSelector("strong[aria-label='Item price']")).getText();
                        price = Double.parseDouble(priceText.replaceAll("[^0-9,\\.]", "").replace(",", "."));
                    } catch (Exception ignored) {}
                    String url = el.getAttribute("href");
                    String image = "";
                    try { image = el.findElement(By.cssSelector("img")).getAttribute("src"); } catch (Exception ignored) {}

                    if (price >= minPrice) {
                        WallapopOffer offer = new WallapopOffer(null, title, priceText, url, price);
                        offer.setImageUrl(image);
                        offers.add(offer);
                    }
                } catch (Exception ignored) {}
            }

            log.info("📦 {} ofertas encontradas para '{}'", offers.size(), query);

        } catch (Exception e) {
            log.error("❌ Error en búsqueda con WebDriver: {}", e.getMessage(), e);
        }

        return offers;
    }
    
    public Map<String, List<WallapopOffer>> searchAllProducts() {
        Map<String, List<WallapopOffer>> results = new HashMap<>();
        for (ProductConfig product : products) {
            List<WallapopOffer> offers = searchProduct(product.getName(), product.getMinPrice());
            if (!offers.isEmpty()) results.put(product.getName(), offers);

            // Delay aleatorio entre búsquedas
            try {
                Thread.sleep(3000 + (long)(Math.random() * 4000));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return results;
    }

    @PostConstruct
    public void testSearch() {
        List<WallapopOffer> offers = searchProduct("ps5", 100);
        offers.forEach(o -> log.info("🎯 {} - {} - {}", o.getTitle(), o.getPrice(), o.getUrl()));
    }

    @PreDestroy
    public void shutdown() {
        if (driver != null) {
            try { driver.quit(); } catch (Exception ignored) {}
        }
        log.info("🛑 WebDriver cerrado");
    }

    public List<ProductConfig> getConfiguredProducts() {
        return new ArrayList<>(products);
    }
}
