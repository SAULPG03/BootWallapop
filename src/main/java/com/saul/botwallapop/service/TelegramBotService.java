package com.saul.botwallapop.service;

import com.saul.botwallapop.model.BotState;
import com.saul.botwallapop.model.WallapopOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class TelegramBotService extends TelegramLongPollingBot {
    
    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);
    
    @Value("${telegram.bot.token}")
    private String botToken;
    
    @Value("${telegram.bot.username}")
    private String botUsername;
    
    private final BotState botState;
    private final WallapopScraperService scraperService;
    
    public TelegramBotService(BotState botState, WallapopScraperService scraperService) {
        this.botState = botState;
        this.scraperService = scraperService;
    }
    
    @Override
    public String getBotUsername() {
        return botUsername;
    }
    
    @Override
    public String getBotToken() {
        return botToken;
    }
    
    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();
            String userId = update.getMessage().getFrom().getId().toString();
            String userName = update.getMessage().getFrom().getFirstName();
            
            log.info("Mensaje recibido de {}: {}", userName, messageText);
            
            // Si es el primer mensaje, autorizar al usuario
            if (botState.getAuthorizedUsers().isEmpty()) {
                botState.addAuthorizedUser(userId);
                log.info("Usuario {} autorizado (primer usuario)", userName);
            }
            
            // Verificar autorización
            if (!botState.isUserAuthorized(userId)) {
                sendMessage(chatId, "❌ No estás autorizado para usar este bot.");
                return;
            }
            
            // Procesar comandos
            switch (messageText.toLowerCase()) {
                case "/start":
                    handleStart(chatId, userName);
                    break;
                case "/help":
                    handleHelp(chatId);
                    break;
                case "▶️ iniciar monitoreo":
                    handleStartMonitoring(chatId);
                    break;
                case "⏸️ pausar monitoreo":
                    handlePauseMonitoring(chatId);
                    break;
                case "🔍 escanear ahora":
                    handleScanNow(chatId);
                    break;
                case "📊 estado":
                    handleStatus(chatId);
                    break;
                case "🔐 login wallapop":
                    handleLogin(chatId);
                    break;
                case "❌ cerrar sesión":
                    handleLogout(chatId);
                    break;
                default:
                    sendMessage(chatId, "❓ Comando no reconocido. Usa /help para ver los comandos disponibles.");
            }
        }
    }
    
    private void handleStart(Long chatId, String userName) {
        String welcomeMessage = String.format(
            "👋 ¡Hola %s! Bienvenido al Bot de Wallapop\n\n" +
            "🤖 Soy tu asistente para monitorear ofertas con ¡NOVEDAD! en tus favoritos de Wallapop.\n\n" +
            "Para empezar:\n" +
            "1️⃣ Primero haz login con: 🔐 Login Wallapop\n" +
            "2️⃣ Luego inicia el monitoreo: ▶️ Iniciar Monitoreo\n\n" +
            "Usa /help para ver todos los comandos disponibles.",
            userName
        );
        
        sendMessageWithKeyboard(chatId, welcomeMessage);
    }
    
    private void handleHelp(Long chatId) {
        String helpMessage = 
            "📚 *Comandos Disponibles*\n\n" +
            "▶️ *Iniciar Monitoreo* - Comienza a escanear favoritos cada 5 minutos\n" +
            "⏸️ *Pausar Monitoreo* - Detiene el escaneo automático\n" +
            "🔍 *Escanear Ahora* - Realiza un escaneo manual inmediato\n" +
            "📊 *Estado* - Muestra el estado actual del bot\n" +
            "🔐 *Login Wallapop* - Inicia sesión en Wallapop\n" +
            "❌ *Cerrar Sesión* - Cierra la sesión de Wallapop\n\n" +
            "💡 *Tip:* Asegúrate de iniciar sesión antes de comenzar el monitoreo.";
        
        sendMessage(chatId, helpMessage);
    }
    
    private void handleStartMonitoring(Long chatId) {
        if (!scraperService.isLoggedIn()) {
            sendMessage(chatId, "⚠️ Primero debes iniciar sesión en Wallapop.\nUsa: 🔐 Login Wallapop");
            return;
        }
        
        botState.setRunning(true);
        sendMessage(chatId, "✅ *Monitoreo iniciado*\n\nEscanearé tus favoritos cada 5 minutos y te notificaré de nuevas ofertas con ¡NOVEDAD!");
        log.info("Monitoreo iniciado");
    }
    
    private void handlePauseMonitoring(Long chatId) {
        botState.setRunning(false);
        sendMessage(chatId, "⏸️ *Monitoreo pausado*\n\nPuedes reactivarlo cuando quieras con: ▶️ Iniciar Monitoreo");
        log.info("Monitoreo pausado");
    }
    
    private void handleScanNow(Long chatId) {
        if (!scraperService.isLoggedIn()) {
            sendMessage(chatId, "⚠️ Primero debes iniciar sesión en Wallapop.");
            return;
        }
        
        sendMessage(chatId, "🔍 Escaneando favoritos...");
        
        try {
            List<WallapopOffer> offers = scraperService.checkFavorites();
            
            if (offers.isEmpty()) {
                sendMessage(chatId, "✅ Escaneo completado\n\nNo se encontraron nuevas ofertas con ¡NOVEDAD!");
            } else {
                sendMessage(chatId, String.format("🎉 Encontradas *%d* ofertas nuevas:", offers.size()));
                
                for (WallapopOffer offer : offers) {
                    if (!botState.getProcessedOfferIds().contains(offer.getId())) {
                        sendOfferNotification(chatId, offer);
                        botState.getProcessedOfferIds().add(offer.getId());
                        botState.setTotalOffersFound(botState.getTotalOffersFound() + 1);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error durante escaneo manual", e);
            sendMessage(chatId, "❌ Error durante el escaneo: " + e.getMessage());
        }
    }
    
    private void handleStatus(Long chatId) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        
        String status = String.format(
            "📊 *Estado del Bot*\n\n" +
            "🟢 Estado: %s\n" +
            "🔐 Sesión Wallapop: %s\n" +
            "📦 Ofertas encontradas: %d\n" +
            "🕐 Último escaneo: %s\n" +
            "👥 Usuarios autorizados: %d",
            botState.isRunning() ? "Activo" : "Pausado",
            scraperService.isLoggedIn() ? "✅ Conectada" : "❌ Desconectada",
            botState.getTotalOffersFound(),
            botState.getLastCheck() != null ? botState.getLastCheck().format(formatter) : "Nunca",
            botState.getAuthorizedUsers().size()
        );
        
        sendMessage(chatId, status);
    }
    
    private void handleLogin(Long chatId) {
        sendMessage(chatId, "🔐 Iniciando sesión en Wallapop...\n\nEsto puede tardar unos segundos.");
        
        try {
            boolean success = scraperService.login();
            
            if (success) {
                botState.setLoggedIn(true);
                sendMessage(chatId, "✅ *Sesión iniciada correctamente*\n\n¡Ya puedes comenzar el monitoreo!");
            } else {
                sendMessage(chatId, "❌ *Error al iniciar sesión*\n\nVerifica tus credenciales en el archivo de configuración.");
            }
        } catch (Exception e) {
            log.error("Error durante login", e);
            sendMessage(chatId, "❌ Error: " + e.getMessage());
        }
    }
    
    private void handleLogout(Long chatId) {
        scraperService.closeDriver();
        botState.setLoggedIn(false);
        botState.setRunning(false);
        sendMessage(chatId, "❌ Sesión cerrada. El monitoreo se ha detenido.");
    }
    
    public void sendOfferNotification(Long chatId, WallapopOffer offer) {
        try {
            SendMessage message = new SendMessage();
            message.setChatId(chatId.toString());
            message.setText(offer.toTelegramMessage());
            message.setParseMode("Markdown");
            message.disableWebPagePreview();
            
            execute(message);
            log.info("Notificación enviada al usuario {}", chatId);
            
        } catch (TelegramApiException e) {
            log.error("Error enviando notificación: {}", e.getMessage());
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
            
            // Crear teclado personalizado
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            List<KeyboardRow> keyboard = new ArrayList<>();
            
            KeyboardRow row1 = new KeyboardRow();
            row1.add(new KeyboardButton("▶️ Iniciar Monitoreo"));
            row1.add(new KeyboardButton("⏸️ Pausar Monitoreo"));
            
            KeyboardRow row2 = new KeyboardRow();
            row2.add(new KeyboardButton("🔍 Escanear Ahora"));
            row2.add(new KeyboardButton("📊 Estado"));
            
            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("🔐 Login Wallapop"));
            row3.add(new KeyboardButton("❌ Cerrar Sesión"));
            
            keyboard.add(row1);
            keyboard.add(row2);
            keyboard.add(row3);
            
            keyboardMarkup.setKeyboard(keyboard);
            keyboardMarkup.setResizeKeyboard(true);
            message.setReplyMarkup(keyboardMarkup);
            
            execute(message);
            
        } catch (TelegramApiException e) {
            log.error("Error enviando mensaje con teclado: {}", e.getMessage());
        }
    }
}
