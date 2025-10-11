package com.saul.botwallapop.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class BotState {
    private boolean isRunning = false;
    private boolean isLoggedIn = false;
    private LocalDateTime lastCheck;
    private int totalOffersFound = 0;
    private Set<String> authorizedUsers = new HashSet<>();
    private Set<String> processedOfferIds = new HashSet<>();
    
    public boolean isRunning() {
        return isRunning;
    }
    
    public void setRunning(boolean running) {
        isRunning = running;
    }
    
    public boolean isLoggedIn() {
        return isLoggedIn;
    }
    
    public void setLoggedIn(boolean loggedIn) {
        isLoggedIn = loggedIn;
    }
    
    public LocalDateTime getLastCheck() {
        return lastCheck;
    }
    
    public void setLastCheck(LocalDateTime lastCheck) {
        this.lastCheck = lastCheck;
    }
    
    public int getTotalOffersFound() {
        return totalOffersFound;
    }
    
    public void setTotalOffersFound(int totalOffersFound) {
        this.totalOffersFound = totalOffersFound;
    }
    
    public Set<String> getAuthorizedUsers() {
        return authorizedUsers;
    }
    
    public void setAuthorizedUsers(Set<String> authorizedUsers) {
        this.authorizedUsers = authorizedUsers;
    }
    
    public Set<String> getProcessedOfferIds() {
        return processedOfferIds;
    }
    
    public void setProcessedOfferIds(Set<String> processedOfferIds) {
        this.processedOfferIds = processedOfferIds;
    }
    
    public void addAuthorizedUser(String userId) {
        authorizedUsers.add(userId);
    }
    
    public boolean isUserAuthorized(String userId) {
        return authorizedUsers.contains(userId);
    }
}