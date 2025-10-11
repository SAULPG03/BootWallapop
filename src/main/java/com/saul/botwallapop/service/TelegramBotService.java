package com.saul.botwallapop.service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.saul.botwallapop.model.BotState;
import com.saul.botwallapop.model.ProductConfig;
import com.saul.botwallapop.model.WallapopOffer;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final BotState botState;
    private final WallapopSearchService searchService;

    public TelegramBotService(BotState botState, WallapopSearchService searchService) {
        this.botState = botState;
        this.searchService = searchService;
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String messageText = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        String userId = update.getMessage().getFrom().getId().toString();
        String userName = update.getMessage().getFrom().getFirstName();

        log.info("Mensaje de {}: {}", userName, messageText);

        // Autorizar primer usuario automáticamente
        if (botState.getAuthorizedUsers().isEmpty()) {
            botState.addAuthorizedUser(userId);
            log.info("Usuario {} autorizado", userName);
        }

        if (!botState.isUserAuthorized(userId)) {
            sendMessage(chatId, "❌ No autorizado");
            return;
        }

        switch (messageText.toLowerCase().trim()) {
            case "/start" -> handleStart(chatId, userName);
            case "/help" -> handleHelp(chatId);
            case "/buscar", "buscar" -> handleSearchAll(chatId);
            case "/productos", "productos" -> handleListProducts(chatId);
            case "/estado", "estado" -> handleStatus(chatId);
            default -> sendMessage(chatId, "❓ Usa /help para ver comandos");
        }
    }

    private void handleStart(Long chatId, String userName) {
        String message = String.format(
            "👋 ¡Hola %s!\n\n🤖 *Bot de Wallapop*\n\n" +
            "Te notificaré automáticamente si encuentro nuevas ofertas.\n\n" +
            "Usa /help para ver comandos", userName
        );
        sendMessageWithKeyboard(chatId, message);
    }

    private void handleHelp(Long chatId) {
        String message =
            "📚 *Comandos*\n\n" +
            "*Buscar* - Buscar todos los productos ahora\n" +
            "*Productos* - Ver productos configurados\n" +
            "*Estado* - Ver estadísticas\n\n" +
            "💡 El bot busca automáticamente cada 10 minutos";
        sendMessage(chatId, message);
    }

    private void handleListProducts(Long chatId) {
        List<ProductConfig> products = searchService.getConfiguredProducts();
        StringBuilder sb = new StringBuilder("📋 *Productos Monitoreados:*\n\n");
        for (int i = 0; i < products.size(); i++) {
            ProductConfig p = products.get(i);
            sb.append(String.format("%d. %s\n   Precio mínimo: %.2f€\n\n",
                i + 1, p.getName(), p.getMinPrice()));
        }
        sendMessage(chatId, sb.toString());
    }

    private void handleStatus(Long chatId) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

        String status = String.format(
            "📊 *Estado*\n\n" +
            "📦 Ofertas enviadas: %d\n" +
            "🕐 Último escaneo: %s\n" +
            "👥 Usuarios autorizados: %d\n" +
            "🔍 Productos: %d",
            botState.getNotifiedOffers().size(),
            botState.getLastCheck() != null ? botState.getLastCheck().format(formatter) : "Nunca",
            botState.getAuthorizedUsers().size(),
            searchService.getConfiguredProducts().size()
        );

        sendMessage(chatId, status);
    }

    private void handleSearchAll(Long chatId) {
        sendMessage(chatId, "🔍 Buscando todos los productos...");

        Map<String, List<WallapopOffer>> results = searchService.searchAllProducts();

        if (results.isEmpty()) {
            sendMessage(chatId, "❌ No se encontraron ofertas que cumplan los criterios");
            return;
        }

        int totalNewOffers = 0;

        for (Map.Entry<String, List<WallapopOffer>> entry : results.entrySet()) {
            String productName = entry.getKey();
            List<WallapopOffer> offers = entry.getValue();

            List<WallapopOffer> newOffersForProduct = new ArrayList<>();
            for (WallapopOffer offer : offers) {
                if (!botState.isOfferNotified(offer.getUrl())) {
                    newOffersForProduct.add(offer);
                }
            }

            if (newOffersForProduct.isEmpty()) continue;

            sendMessage(chatId, String.format(
                "📦 *%s*\nEncontradas %d nuevas ofertas:",
                productName, newOffersForProduct.size()
            ));

            for (WallapopOffer offer : newOffersForProduct) {
                sendOfferNotification(chatId, offer);
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }

            totalNewOffers += newOffersForProduct.size();
        }

        sendMessage(chatId, String.format("✅ Total nuevas ofertas enviadas: %d", totalNewOffers));
    }

    public void sendOfferNotification(Long chatId, WallapopOffer offer) {
        if (botState.isOfferNotified(offer.getUrl())) return;

        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(offer.toTelegramMessage());
            message.setParseMode("Markdown");
            message.disableWebPagePreview();
            execute(message);

            botState.markOfferAsNotified(offer.getUrl());
        } catch (TelegramApiException e) {
            log.error("Error enviando oferta: {}", e.getMessage());
        }
    }

    private void sendMessage(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error enviando mensaje: {}", e.getMessage());
        }
    }

    private void sendMessageWithKeyboard(Long chatId, String text) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(text);
            message.setParseMode("Markdown");

            ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
            List<KeyboardRow> rows = new ArrayList<>();

            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton("Buscar"));
            row1.add(new KeyboardButton("Productos"));

            KeyboardRow row2 = new KeyboardRow();
            row2.add(new KeyboardButton("Estado"));

            rows.add(row1);
            rows.add(row2);

            keyboard.setKeyboard(rows);
            keyboard.setResizeKeyboard(true);
            message.setReplyMarkup(keyboard);

            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error enviando mensaje con teclado: {}", e.getMessage());
        }
    }

    // 🔹 Notificaciones automáticas cada 10 minutos
    @Scheduled(fixedRate = 600_000) // 600_000 ms = 10 minutos
    public void scheduledSearchAndNotify() {
        for (String userId : botState.getAuthorizedUsers()) {
            handleSearchAll(Long.parseLong(userId));
        }
    }
}
