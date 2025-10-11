package com.saul.botwallapop.scheduler;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;

import com.saul.botwallapop.model.BotState;
import com.saul.botwallapop.model.WallapopOffer;
import com.saul.botwallapop.service.TelegramBotService;
import com.saul.botwallapop.service.WallapopScraperService;

@Component
public class WallapopScheduler {
    
    private static final Logger log = LoggerFactory.getLogger(WallapopScheduler.class);
    
    private final WallapopScraperService scraperService;
    private final TelegramBotService telegramBotService;
    private final BotState botState;
    
    public WallapopScheduler(WallapopScraperService scraperService,
                            TelegramBotService telegramBotService,
                            BotState botState) {
        this.scraperService = scraperService;
        this.telegramBotService = telegramBotService;
        this.botState = botState;
    }
    
    @Scheduled(fixedDelayString = "${wallapop.check.interval.minutes:5}0000", initialDelay = 30000)
    public void checkWallapopFavorites() {
        // Solo escanear si el bot está activo
        if (!botState.isRunning()) {
            log.debug("Bot pausado, saltando escaneo");
            return;
        }
        
        if (!scraperService.isLoggedIn()) {
            log.warn("No hay sesión activa en Wallapop, saltando escaneo");
            return;
        }
        
        log.info("🔍 Iniciando escaneo automático de Wallapop...");
        
        try {
            List<WallapopOffer> offers = scraperService.checkFavorites();
            botState.setLastCheck(LocalDateTime.now());
            
            if (offers.isEmpty()) {
                log.info("✅ Escaneo completado. No hay nuevas ofertas");
            } else {
                log.info("🎉 Encontradas {} ofertas con NOVEDAD", offers.size());
                
                // Enviar notificaciones a todos los usuarios autorizados
                for (String userId : botState.getAuthorizedUsers()) {
                    for (WallapopOffer offer : offers) {
                        // Evitar duplicados
                        if (!botState.getProcessedOfferIds().contains(offer.getId())) {
                            telegramBotService.sendOfferNotification(Long.parseLong(userId), offer);
                            botState.getProcessedOfferIds().add(offer.getId());
                            botState.setTotalOffersFound(botState.getTotalOffersFound() + 1);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("❌ Error durante el escaneo automático: {}", e.getMessage(), e);
            
            // Notificar el error a los usuarios
            for (String userId : botState.getAuthorizedUsers()) {
                try {
                    SendMessage errorMsg = new SendMessage();
                    errorMsg.setChatId(userId);
                    errorMsg.setText("⚠️ Error durante el escaneo automático: " + e.getMessage());
                    // No enviamos para evitar spam de errores
                } catch (Exception notificationError) {
                    log.error("Error enviando notificación de error", notificationError);
                }
            }
        }
    }
}
