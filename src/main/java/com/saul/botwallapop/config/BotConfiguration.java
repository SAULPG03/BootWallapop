package com.saul.botwallapop.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import com.saul.botwallapop.model.BotState;
import com.saul.botwallapop.service.TelegramBotService;
import com.saul.botwallapop.service.WallapopScraperService;



@Configuration
public class BotConfiguration {
    private static final Logger log = LoggerFactory.getLogger(BotConfiguration.class);

    @Bean
    public BotState botState() {
        return new BotState();
    }
    
    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotService telegramBotService) {
        try {
            TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
            api.registerBot(telegramBotService);
            log.info("✅ Bot de Telegram registrado correctamente");
            return api;
        } catch (TelegramApiException e) {
            log.error("❌ Error registrando el bot de Telegram: {}", e.getMessage());
            throw new RuntimeException("No se pudo registrar el bot", e);
        }
    }
}

