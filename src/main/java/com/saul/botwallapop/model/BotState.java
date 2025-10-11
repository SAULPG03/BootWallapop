package com.saul.botwallapop.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class BotState {
    private LocalDateTime lastCheck;
    private int totalOffersFound = 0;
    private Set<String> authorizedUsers = new HashSet<>();
    private Set<String> notifiedOfferIds = new HashSet<>();
    
    public LocalDateTime getLastCheck() { return lastCheck; }
    public void setLastCheck(LocalDateTime lastCheck) { this.lastCheck = lastCheck; }
    
    public int getTotalOffersFound() { return totalOffersFound; }
    public void setTotalOffersFound(int totalOffersFound) { this.totalOffersFound = totalOffersFound; }
    
    public Set<String> getAuthorizedUsers() { return authorizedUsers; }
    public void addAuthorizedUser(String userId) { authorizedUsers.add(userId); }
    public boolean isUserAuthorized(String userId) { return authorizedUsers.contains(userId); }
    
    public Set<String> getNotifiedOfferIds() { return notifiedOfferIds; }
    public boolean wasNotified(String offerId) { return notifiedOfferIds.contains(offerId); }
    public void addNotified(String offerId) { notifiedOfferIds.add(offerId); }
}
