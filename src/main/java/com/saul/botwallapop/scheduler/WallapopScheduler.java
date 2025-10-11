package com.saul.botwallapop.scheduler;

import com.saul.botwallapop.model.BotState;
import com.saul.botwallapop.model.WallapopOffer;
import com.saul.botwallapop.service.TelegramBotService;
import com.saul.botwallapop.service.WallapopSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
public class WallapopScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(WallapopScheduler.class);
    
    private final WallapopSearchService searchService;
    private final TelegramBotService telegramService;
    private final BotState botState;
    
    @Value("${wallapop.check.interval.minutes:10}")
    private int checkInterval;
    
    public WallapopScheduler(WallapopSearchService searchService,
                            TelegramBotService telegramService,
                            BotState botState) {
        this.searchService = searchService;
        this.telegramService = telegramService;
        this.botState = botState;
    }
    
    @Scheduled(fixedDelayString = "${wallapop.check.interval.minutes:10}0000", 
               initialDelay = 60000)
    public void checkProducts() {
        log.info("🔍 Escaneando productos configurados...");
        
        Map<String, List<WallapopOffer>> results = searchService.searchAllProducts();
        botState.setLastCheck(LocalDateTime.now());
        
        if (results.isEmpty()) {
            log.info("✅ No hay ofertas nuevas");
            return;
        }
        
        int newOffers = 0;
        
        for (Map.Entry<String, List<WallapopOffer>> entry : results.entrySet()) {
            String productName = entry.getKey();
            List<WallapopOffer> offers = entry.getValue();
            
            log.info("📦 {}: {} ofertas", productName, offers.size());
            
            // Notificar a todos los usuarios autorizados
            for (String userId : botState.getAuthorizedUsers()) {
                Long chatId = Long.parseLong(userId);
                
                for (WallapopOffer offer : offers) {
                    // Evitar notificar la misma oferta dos veces
                    if (!botState.wasNotified(offer.getId())) {
                        telegramService.sendMessage(chatId, 
                            String.format("🎉 *Nueva oferta de %s*", productName));
                        telegramService.sendOfferNotification(chatId, offer);
                        
                        botState.addNotified(offer.getId());
                        newOffers++;
                        
                        try { Thread.sleep(1000); } 
                        catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        
        if (newOffers > 0) {
            botState.setTotalOffersFound(botState.getTotalOffersFound() + newOffers);
            log.info("🎉 {} nuevas ofertas notificadas", newOffers);
        }
    }
}
