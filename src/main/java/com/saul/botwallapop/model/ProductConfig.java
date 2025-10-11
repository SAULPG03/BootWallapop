package com.saul.botwallapop.model;

public class ProductConfig {
    private String name;
    private double minPrice;
    
    public ProductConfig() {}
    
    public ProductConfig(String name, double minPrice) {
        this.name = name;
        this.minPrice = minPrice;
    }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public double getMinPrice() { return minPrice; }
    public void setMinPrice(double minPrice) { this.minPrice = minPrice; }
    
    @Override
    public String toString() {
        return String.format("%s (min: %.2f€)", name, minPrice);
    }
}
