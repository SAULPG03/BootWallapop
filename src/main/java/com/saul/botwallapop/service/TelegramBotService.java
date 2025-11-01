package com.saul.botwallapop.service;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import com.saul.botwallapop.model.*;

@Component
public class TelegramBotService extends TelegramLongPollingBot {

    private static final Logger log = LoggerFactory.getLogger(TelegramBotService.class);

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    private final BotState botState;
    private final WallapopSearchService searchService;

    // Cola de mensajes a enviar
    private final BlockingQueue<SendMessage> messageQueue = new LinkedBlockingQueue<>();

    // Lista de mensajes enviados (para /limpiar)
    private final List<Integer> mensajesEnviados = new CopyOnWriteArrayList<>();

    // Ejecutores
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    // ID del último grupo donde se recibió un mensaje
    @Value("${telegram.group.id}")
    private volatile Long lastGroupChatId;

    public TelegramBotService(BotState botState, WallapopSearchService searchService) {
        this.botState = botState;
        this.searchService = searchService;
        iniciarProcesadorDeCola();
    }

    @Override
    public String getBotUsername() { return botUsername; }

    @Override
    public String getBotToken() { return botToken; }

    // -------------------- PROCESADOR DE COLA --------------------
    private void iniciarProcesadorDeCola() {
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                SendMessage msg = messageQueue.poll(1, TimeUnit.SECONDS);
                if (msg != null) {
                    enviarConReintentos(msg);
                    Thread.sleep(40); // 25 mensajes por segundo máx (rate limit Telegram)
                }
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                log.error("Error procesando la cola de mensajes: {}", e.getMessage());
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    private void enviarConReintentos(SendMessage msg) {
        int intentos = 0;
        int maxIntentos = 5;
        long espera = 1000; // ms
        while (intentos < maxIntentos) {
            try {
                var result = execute(msg);
                mensajesEnviados.add(result.getMessageId());
                return;
            } catch (TelegramApiException e) {
                intentos++;
                if (e.getMessage().contains("Too Many Requests")) {
                    espera *= 2; // backoff exponencial
                    log.warn("Rate limit alcanzado. Esperando {}ms antes de reintentar...", espera);
                } else {
                    log.error("Error enviando mensaje (intento {}): {}", intentos, e.getMessage());
                }
                try { Thread.sleep(espera); } catch (InterruptedException ignored) {}
            }
        }
        log.error("❌ No se pudo enviar el mensaje tras {} intentos", maxIntentos);
    }

    // -------------------- MANEJO DE UPDATES --------------------
    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;

        String text = update.getMessage().getText().trim().toLowerCase();
        String userId = update.getMessage().getFrom().getId().toString();
        String userName = update.getMessage().getFrom().getFirstName();
        Long chatId = update.getMessage().getChatId();

        log.info("Mensaje recibido de {}: {}", userName, text);

        // Solo responde en grupos
        if (!update.getMessage().isGroupMessage() && !update.getMessage().isSuperGroupMessage()) {
            log.info("Ignorando mensaje fuera de un grupo.");
            return;
        }

        // Guardamos este chat ID como último grupo activo
        lastGroupChatId = chatId;

        if (botState.getAuthorizedUsers().isEmpty()) botState.addAuthorizedUser(userId);
        if (!botState.isUserAuthorized(userId)) {
            enqueueTextToLastGroup("❌ No autorizado");
            return;
        }

        switch (text) {
            case "/start" -> handleStart();
            case "/help" -> handleHelp();
            case "/buscar", "buscar" -> handleSearchAll(false);
            case "/productos", "productos" -> handleListProducts();
            case "/estado", "estado" -> handleStatus();
            case "/limpiar", "limpiar" -> handleLimpiar();
            default -> enqueueTextToLastGroup("❓ Usa /help para ver comandos disponibles");
        }
    }

    // -------------------- COMANDOS --------------------
    private void handleStart() {
        String msg = """
            👋 ¡Hola!

            🤖 *Bot de Wallapop*
            Te notificaré automáticamente si encuentro nuevas ofertas.

            Usa /help para ver todos los comandos.
            """;
        enqueueTextWithKeyboard(msg);
    }

    private void handleHelp() {
        enqueueTextToLastGroup("""
            📚 *Comandos disponibles:*

            /buscar - Buscar todos los productos ahora
            /productos - Ver productos configurados
            /estado - Ver estadísticas del bot
            /limpiar - Borrar los mensajes del bot en el grupo

            💡 El bot busca automáticamente cada 10 minutos.
            """);
    }

    private void handleListProducts() {
        List<ProductConfig> products = searchService.getConfiguredProducts();
        if (products.isEmpty()) {
            enqueueTextToLastGroup("📦 No hay productos configurados aún.");
            return;
        }

        StringBuilder sb = new StringBuilder("📋 *Productos Monitoreados:*\n\n");
        for (int i = 0; i < products.size(); i++) {
            ProductConfig p = products.get(i);
            sb.append(String.format("%d. %s\n   Precio mínimo: %.2f€\n\n", i + 1, p.getName(), p.getMinPrice()));
        }
        enqueueTextToLastGroup(sb.toString());
    }

    private void handleStatus() {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
        String msg = String.format("""
            📊 *Estado del Bot*

            📦 Ofertas enviadas: %d
            🕐 Último escaneo: %s
            👥 Usuarios autorizados: %d
            🔍 Productos configurados: %d
            """,
            botState.getNotifiedOffers().size(),
            botState.getLastCheck() != null ? botState.getLastCheck().format(fmt) : "Nunca",
            botState.getAuthorizedUsers().size(),
            searchService.getConfiguredProducts().size()
        );
        enqueueTextToLastGroup(msg);
    }

    private void handleSearchAll(boolean automatic) {
        if (!automatic) enqueueTextToLastGroup("🔍 Buscando todos los productos...");

        Map<String, List<WallapopOffer>> results = searchService.searchAllProducts();
        if (results.isEmpty()) {
            if (!automatic) enqueueTextToLastGroup("❌ No se encontraron ofertas que cumplan los criterios");
            return;
        }

        int total = 0;
        for (var entry : results.entrySet()) {
            String product = entry.getKey();
            List<WallapopOffer> offers = entry.getValue();
            List<WallapopOffer> newOffers = offers.stream()
                .filter(o -> !botState.isOfferNotified(o.getUrl()))
                .toList();

            if (newOffers.isEmpty()) continue;

            if (!automatic) enqueueTextToLastGroup(String.format("📦 *%s*\nEncontradas %d nuevas ofertas:", product, newOffers.size()));

            for (WallapopOffer o : newOffers) {
                if (automatic) sendOfferToLastGroup(o);
                else sendOffer(o);
            }
            total += newOffers.size();
        }

        if (!automatic) enqueueTextToLastGroup("✅ Total nuevas ofertas enviadas: " + total);
        else if (total > 0) enqueueTextToLastGroup("✅ Total nuevas ofertas enviadas: " + total);
    }

    private void handleLimpiar() {
        if (mensajesEnviados.isEmpty()) {
            enqueueTextToLastGroup("⚠️ No hay mensajes que borrar.");
            return;
        }
        int borrados = 0;
        for (Integer id : new ArrayList<>(mensajesEnviados)) {
            if (deleteMessage(lastGroupChatId, id)) borrados++;
        }
        mensajesEnviados.clear();
        enqueueTextToLastGroup("🧹 Chat limpiado (" + borrados + " mensajes borrados).");
    }

    // -------------------- UTILIDADES DE ENVÍO --------------------
    public void enqueueTextToLastGroup(String text) {
        if (lastGroupChatId == null) {
            log.warn("No hay grupo activo para enviar el mensaje automático.");
            return;
        }
        SendMessage msg = new SendMessage(lastGroupChatId.toString(), text);
        msg.setParseMode("Markdown");
        messageQueue.offer(msg);
    }

    private void enqueueTextWithKeyboard(String text) {
        if (lastGroupChatId == null) return;

        SendMessage msg = new SendMessage(lastGroupChatId.toString(), text);
        msg.setParseMode("Markdown");

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Buscar"));
        row1.add(new KeyboardButton("Productos"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("Estado"));
        row2.add(new KeyboardButton("Limpiar"));

        keyboard.setKeyboard(List.of(row1, row2));
        msg.setReplyMarkup(keyboard);

        messageQueue.offer(msg);
    }

    public void sendOffer(WallapopOffer offer) {
        if (lastGroupChatId == null) return;
        if (botState.isOfferNotified(offer.getUrl())) return;
        SendMessage msg = new SendMessage(lastGroupChatId.toString(), offer.toTelegramMessage());
        msg.setParseMode("Markdown");
        msg.disableWebPagePreview();
        messageQueue.offer(msg);
        botState.markOfferAsNotified(offer.getUrl());
    }

    private void sendOfferToLastGroup(WallapopOffer offer) {
        sendOffer(offer); // misma lógica
    }

    public boolean deleteMessage(Long chatId, Integer messageId) {
        try {
            execute(new DeleteMessage(chatId.toString(), messageId));
            return true;
        } catch (TelegramApiException e) {
            log.warn("No se pudo borrar el mensaje {}: {}", messageId, e.getMessage());
            return false;
        }
    }

    // -------------------- ESCANEO AUTOMÁTICO --------------------
    @Scheduled(fixedRate = 600_000)
    public void scheduledSearchAndNotify() {
        log.info("Ejecutando búsqueda automática...");
        handleSearchAll(true);
    }
}
