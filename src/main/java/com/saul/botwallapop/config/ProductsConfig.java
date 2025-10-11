package com.saul.botwallapop.config;

import com.saul.botwallapop.model.ProductConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "wallapop")
public class ProductsConfig {
    
    private List<String> products = new ArrayList<>();
    
    public List<String> getProducts() {
        return products;
    }
    
    public void setProducts(List<String> products) {
        this.products = products;
    }
    
    public List<ProductConfig> parseProducts() {
        List<ProductConfig> configs = new ArrayList<>();
        
        for (String product : products) {
            String[] parts = product.split("\\|");
            if (parts.length == 2) {
                try {
                    String name = parts[0].trim();
                    double minPrice = Double.parseDouble(parts[1].trim());
                    configs.add(new ProductConfig(name, minPrice));
                } catch (NumberFormatException e) {
                    System.err.println("Error parseando: " + product);
                }
            }
        }
        
        return configs;
    }
}
