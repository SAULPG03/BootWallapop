package com.saul.botwallapop.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class WallapopOffer {
    private String id;
    private String title;
    private String price;
    private String url;
    private String imageUrl;
    private boolean isNew;
    private LocalDateTime detectedAt;
    
    public WallapopOffer() {
    }
    
    public WallapopOffer(String id, String title, String price, String url) {
        this.id = id;
        this.title = title;
        this.price = price;
        this.url = url;
        this.isNew = true;
        this.detectedAt = LocalDateTime.now();
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getPrice() {
        return price;
    }
    
    public void setPrice(String price) {
        this.price = price;
    }
    
    public String getUrl() {
        return url;
    }
    
    public void setUrl(String url) {
        this.url = url;
    }
    
    public String getImageUrl() {
        return imageUrl;
    }
    
    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }
    
    public boolean isNew() {
        return isNew;
    }
    
    public void setNew(boolean aNew) {
        isNew = aNew;
    }
    
    public LocalDateTime getDetectedAt() {
        return detectedAt;
    }
    
    public void setDetectedAt(LocalDateTime detectedAt) {
        this.detectedAt = detectedAt;
    }
    
    public String toTelegramMessage() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        return String.format(
            "🆕 *NOVEDAD en Wallapop*\n\n" +
            "📦 *%s*\n" +
            "💰 Precio: *%s*\n" +
            "🔗 [Ver oferta](%s)\n" +
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
                   .replace("~", "\\~")
                   .replace("`", "\\`")
                   .replace(">", "\\>")
                   .replace("#", "\\#")
                   .replace("+", "\\+")
                   .replace("-", "\\-")
                   .replace("=", "\\=")
                   .replace("|", "\\|")
                   .replace("{", "\\{")
                   .replace("}", "\\}")
                   .replace(".", "\\.")
                   .replace("!", "\\!");
    }
}