package com.saul.botwallapop.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WallapopOffer {
    private String id;
    private String title;
    private String price;
    private String url;
    private String imageUrl;
    private double priceValue;
    private LocalDateTime detectedAt;
    
    public WallapopOffer() {
    }
    
    public WallapopOffer(String id, String title, String price, String url, double priceValue) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.url = url;
        this.priceValue = priceValue;
        this.detectedAt = LocalDateTime.now();
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getPrice() { return price; }
    public void setPrice(String price) { this.price = price; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public double getPriceValue() { return priceValue; }
    public void setPriceValue(double priceValue) { this.priceValue = priceValue; }
    
    public LocalDateTime getDetectedAt() { return detectedAt; }
    public void setDetectedAt(LocalDateTime detectedAt) { this.detectedAt = detectedAt; }
    
    public String toTelegramMessage() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return String.format(
            "🆕 *Oferta Encontrada*\n\n" +
            "📦 %s\n" +
            "💰 *%s*\n" +
            "🔗 [Ver en Wallapop](%s)\n" +
            "⏰ %s",
            escapeMarkdown(title),
            escapeMarkdown(price),
            url,
            detectedAt.format(formatter)
        );
    }
    
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text.replace("_", "\\_")
                   .replace("*", "\\*")
                   .replace("[", "\\[")
                   .replace("]", "\\]")
                   .replace("(", "\\(")
                   .replace(")", "\\)")
                   .replace("`", "\\`");
    }
}
