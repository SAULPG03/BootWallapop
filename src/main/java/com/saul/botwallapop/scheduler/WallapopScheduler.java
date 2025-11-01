package com.saul.botwallapop.scheduler;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.saul.botwallapop.model.BotState;
import com.saul.botwallapop.model.WallapopOffer;
import com.saul.botwallapop.service.TelegramBotService;
import com.saul.botwallapop.service.WallapopSearchService;

@Component
public class WallapopScheduler {

    private static final Logger log = LoggerFactory.getLogger(WallapopScheduler.class);

    private final BotState botState;
    private final WallapopSearchService searchService;
    private final TelegramBotService telegramBot;

    public WallapopScheduler(BotState botState, WallapopSearchService searchService, TelegramBotService telegramBot) {
        this.botState = botState;
        this.searchService = searchService;
        this.telegramBot = telegramBot;
    }

    @Scheduled(fixedRate = 300_000) // cada 5 minutos
    public void checkProducts() {
        log.info("🔍 Iniciando escaneo automático de productos...");

        Map<String, List<WallapopOffer>> results = searchService.searchAllProducts();
        int totalNewOffers = 0;

        for (Map.Entry<String, List<WallapopOffer>> entry : results.entrySet()) {
            String productName = entry.getKey();
            List<WallapopOffer> offers = entry.getValue();

            // Filtrar ofertas nuevas
            List<WallapopOffer> newOffers = offers.stream()
                    .filter(o -> !botState.isOfferNotified(o.getUrl()))
                    .toList();

            if (newOffers.isEmpty()) continue;

            // Enviar mensaje al grupo
            telegramBot.enqueueTextToLastGroup(String.format(
                    "📦 *%s*\nSe han encontrado %d nuevas ofertas:",
                    productName, newOffers.size()
            ));

            // Enviar cada oferta al grupo
            for (WallapopOffer offer : newOffers) {
                telegramBot.sendOffer(offer);
            }

            totalNewOffers += newOffers.size();
        }

        botState.setLastCheck(LocalDateTime.now());
        log.info("✅ Escaneo finalizado. Total nuevas ofertas enviadas: {}", totalNewOffers);
    }
}
