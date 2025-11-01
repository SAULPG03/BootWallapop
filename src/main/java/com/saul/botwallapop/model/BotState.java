package com.saul.botwallapop.model;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class BotState {

    private LocalDateTime lastCheck;
    private Set<String> authorizedUsers = new HashSet<>();
    private Set<String> notifiedOfferUrls = new HashSet<>();

    public LocalDateTime getLastCheck() { return lastCheck; }
    public void setLastCheck(LocalDateTime lastCheck) { this.lastCheck = lastCheck; }

    public Set<String> getAuthorizedUsers() { return authorizedUsers; }
    public void addAuthorizedUser(String userId) { authorizedUsers.add(userId); }
    public boolean isUserAuthorized(String userId) { return authorizedUsers.contains(userId); }

    // --- Control de ofertas notificadas ---
    public boolean isOfferNotified(String offerUrl) {
        return notifiedOfferUrls.contains(offerUrl);
    }

    public void markOfferAsNotified(String offerUrl) {
        notifiedOfferUrls.add(offerUrl);
    }

    public Set<String> getNotifiedOffers() { return notifiedOfferUrls; }
}
