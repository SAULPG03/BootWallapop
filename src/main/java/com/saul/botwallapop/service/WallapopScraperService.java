package com.saul.botwallapop.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.saul.botwallapop.model.WallapopOffer;

import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class WallapopScraperService {
    private static final Logger log = LoggerFactory.getLogger(WallapopScraperService.class);
    
    private WebDriver driver;
    
    @Value("${wallapop.username}")
    private String username;
    
    @Value("${wallapop.password}")
    private String password;
    
    private boolean isLoggedIn = false;
    
    @PostConstruct
    public void init() {
        WebDriverManager.chromedriver().setup();
        log.info("WebDriver inicializado correctamente");
    }
    
    private void initializeDriver() {
        if (driver == null) {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("--disable-gpu");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
            options.setExperimentalOption("useAutomationExtension", false);
            
            driver = new ChromeDriver(options);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            
            log.info("Chrome driver iniciado en modo headless");
        }
    }
    
    public boolean login() {
        try {
            initializeDriver();
            
            log.info("Navegando a Wallapop...");
            driver.get("https://es.wallapop.com");
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            
            // Aceptar cookies
            try {
                WebElement acceptCookies = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(text(), 'Aceptar todas') or contains(text(), 'Aceptar') or contains(@id, 'onetrust-accept')]")));
                acceptCookies.click();
                log.info("Cookies aceptadas");
                Thread.sleep(1000);
            } catch (Exception e) {
                log.info("No se encontró popup de cookies o ya fue aceptado");
            }
            
            // Click en "Iniciar sesión"
            WebElement loginBtn = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//button[contains(text(), 'Iniciar sesión') or contains(@class, 'header') and contains(., 'Iniciar')]")));
            loginBtn.click();
            log.info("Click en botón de inicio de sesión");
            Thread.sleep(2000);
            
            // Rellenar email
            WebElement emailInput = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@type='email' or @name='username' or contains(@placeholder, 'Email')]")));
            emailInput.clear();
            emailInput.sendKeys(username);
            log.info("Email ingresado");
            
            // Rellenar contraseña
            WebElement passwordInput = driver.findElement(
                By.xpath("//input[@type='password' or @name='password']"));
            passwordInput.clear();
            passwordInput.sendKeys(password);
            log.info("Contraseña ingresada");
            
            // Click en continuar/entrar
            WebElement submitBtn = driver.findElement(
                By.xpath("//button[@type='submit' or contains(text(), 'Continuar') or contains(text(), 'Entrar')]"));
            submitBtn.click();
            log.info("Formulario enviado");
            
            // Esperar a que cargue la página principal
            Thread.sleep(5000);
            
            // Verificar que se ha iniciado sesión correctamente
            try {
                wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.xpath("//a[contains(@href, '/profile')]")),
                    ExpectedConditions.presenceOfElementLocated(By.xpath("//button[contains(@class, 'user') or contains(@class, 'profile')]"))
                ));
                isLoggedIn = true;
                log.info("✅ Sesión iniciada correctamente en Wallapop");
                return true;
            } catch (Exception e) {
                log.error("❌ No se pudo verificar el inicio de sesión");
                return false;
            }
            
        } catch (Exception e) {
            log.error("❌ Error durante el login: {}", e.getMessage(), e);
            return false;
        }
    }
    
    public List<WallapopOffer> checkFavorites() {
        List<WallapopOffer> newOffers = new ArrayList<>();
        
        try {
            if (!isLoggedIn) {
                log.warn("No hay sesión activa, intentando login...");
                if (!login()) {
                    throw new RuntimeException("No se pudo iniciar sesión");
                }
            }
            
            log.info("Navegando a favoritos...");
            driver.get("https://es.wallapop.com/app/favorites");
            Thread.sleep(3000);
            
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));
            
            // Esperar a que carguen los productos
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'ItemCard') or contains(@data-testid, 'item-card')]")),
                ExpectedConditions.presenceOfElementLocated(By.xpath("//div[contains(@class, 'empty') or contains(text(), 'No tienes favoritos')]"))
            ));
            
            // Scroll para cargar todos los productos
            JavascriptExecutor js = (JavascriptExecutor) driver;
            for (int i = 0; i < 3; i++) {
                js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1000);
            }
            
            // Buscar productos
            List<WebElement> productCards = driver.findElements(
                By.xpath("//div[contains(@class, 'ItemCard') or contains(@class, 'item-card') or @data-testid='item-card']"));
            
            log.info("📦 Encontrados {} productos en favoritos", productCards.size());
            
            for (WebElement card : productCards) {
                try {
                    // Verificar si tiene etiqueta de NOVEDAD
                    boolean hasNovedadTag = false;
                    try {
                        card.findElement(By.xpath(".//span[contains(text(), 'NOVEDAD') or contains(text(), '¡NOVEDAD!') or contains(@class, 'bump')]"));
                        hasNovedadTag = true;
                    } catch (NoSuchElementException e) {
                        // No tiene tag de novedad
                        continue;
                    }
                    
                    if (hasNovedadTag) {
                        // Extraer información
                        String title = card.findElement(By.xpath(".//h3 | .//p[contains(@class, 'ItemCard__title')]")).getText();
                        String price = card.findElement(By.xpath(".//span[contains(@class, 'price') or contains(@class, 'Price')]")).getText();
                        String url = card.findElement(By.xpath(".//a")).getAttribute("href");
                        
                        // Generar ID único basado en la URL
                        String offerId = url.split("/item/")[1].split("\\?")[0];
                        
                        WallapopOffer offer = new WallapopOffer(offerId, title, price, url);
                        newOffers.add(offer);
                        
                        log.info("🆕 Nueva oferta encontrada: {} - {}", title, price);
                    }
                    
                } catch (Exception e) {
                    log.debug("Error procesando un producto: {}", e.getMessage());
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Error escaneando favoritos: {}", e.getMessage(), e);
            isLoggedIn = false;
        }
        
        return newOffers;
    }
    
    public boolean isLoggedIn() {
        return isLoggedIn;
    }
    
    @PreDestroy
    public void closeDriver() {
        if (driver != null) {
            try {
                driver.quit();
                log.info("Driver cerrado correctamente");
            } catch (Exception e) {
                log.error("Error cerrando driver: {}", e.getMessage());
            }
            driver = null;
            isLoggedIn = false;
        }
    }
}

