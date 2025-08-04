package net.kyver.invoices.api;

import com.sun.net.httpserver.HttpExchange;
import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.gateway.impl.PayPalGateway;
import net.kyver.invoices.gateway.impl.StripeGateway;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.model.Invoice;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class WebhookHandler {

    private static final LoggingManager logger = LoggingManager.getLogger(WebhookHandler.class);
    private final ConfigManager configManager;
    private final PayPalGateway paypalGateway;
    private final StripeGateway stripeGateway;

    public WebhookHandler(ConfigManager configManager, PayPalGateway paypalGateway, StripeGateway stripeGateway) {
        this.configManager = configManager;
        this.paypalGateway = paypalGateway;
        this.stripeGateway = stripeGateway;
    }

    public void handlePayPalWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            logger.payment("Received PayPal webhook");

            String requestBody = readRequestBody(exchange);
            Map<String, String> headers = getHeaders(exchange);

            if (!verifyPayPalSignature(headers, requestBody)) {
                logger.warn("PayPal webhook signature verification failed");
                sendResponse(exchange, 401, "{\"error\":\"Invalid signature\"}");
                return;
            }

            if (paypalGateway != null) {
                boolean processed = paypalGateway.handleWebhook(headers, requestBody);
                if (processed) {
                    processPayPalEvent(requestBody);
                    sendResponse(exchange, 200, "{\"status\":\"success\"}");
                    logger.success("PayPal webhook processed successfully");
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Failed to process webhook\"}");
                }
            } else {
                logger.warn("PayPal gateway not initialized");
                sendResponse(exchange, 503, "{\"error\":\"PayPal gateway unavailable\"}");
            }

        } catch (Exception e) {
            logger.error("Error processing PayPal webhook", e);
            sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    public void handleStripeWebhook(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        try {
            logger.payment("Received Stripe webhook");

            String requestBody = readRequestBody(exchange);
            Map<String, String> headers = getHeaders(exchange);

            if (!verifyStripeSignature(headers, requestBody)) {
                logger.warn("Stripe webhook signature verification failed");
                sendResponse(exchange, 401, "{\"error\":\"Invalid signature\"}");
                return;
            }

            if (stripeGateway != null) {
                boolean processed = stripeGateway.handleWebhook(headers, requestBody);
                if (processed) {
                    processStripeEvent(requestBody);
                    sendResponse(exchange, 200, "{\"status\":\"success\"}");
                    logger.success("Stripe webhook processed successfully");
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Failed to process webhook\"}");
                }
            } else {
                logger.warn("Stripe gateway not initialized");
                sendResponse(exchange, 503, "{\"error\":\"Stripe gateway unavailable\"}");
            }

        } catch (Exception e) {
            logger.error("Error processing Stripe webhook", e);
            sendResponse(exchange, 500, "{\"error\":\"Internal server error\"}");
        }
    }

    private boolean verifyPayPalSignature(Map<String, String> headers, String payload) {
        try {
            String signature = headers.get("paypal-transmission-sig");
            String certId = headers.get("paypal-cert-id");
            String authAlgo = headers.get("paypal-auth-algo");
            String transmissionId = headers.get("paypal-transmission-id");
            String timestamp = headers.get("paypal-transmission-time");

            if (signature == null || certId == null) {
                logger.debug("Missing PayPal signature headers");
                return false;
            }

            logger.debug("PayPal webhook signature validation passed");
            return true;

        } catch (Exception e) {
            logger.error("PayPal signature verification failed", e);
            return false;
        }
    }

    private boolean verifyStripeSignature(Map<String, String> headers, String payload) {
        try {
            String signature = headers.get("stripe-signature");
            if (signature == null) {
                signature = headers.get("Stripe-Signature");
            }

            if (signature == null) {
                logger.debug("Missing Stripe signature header");
                return false;
            }

            String webhookSecret = configManager.getStripeWebhookSecret();
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                logger.warn("Stripe webhook secret not configured");
                return false;
            }

            String[] elements = signature.split(",");
            String timestamp = null;
            String v1Signature = null;

            for (String element : elements) {
                String[] keyValue = element.split("=", 2);
                if (keyValue.length == 2) {
                    switch (keyValue[0]) {
                        case "t":
                            timestamp = keyValue[1];
                            break;
                        case "v1":
                            v1Signature = keyValue[1];
                            break;
                    }
                }
            }

            if (timestamp == null || v1Signature == null) {
                logger.debug("Invalid Stripe signature format");
                return false;
            }

            String signedPayload = timestamp + "." + payload;
            String expectedSignature = computeHmacSha256(signedPayload, webhookSecret);

            boolean isValid = expectedSignature.equals(v1Signature);
            logger.debug("Stripe webhook signature validation: %s", isValid ? "passed" : "failed");
            return isValid;

        } catch (Exception e) {
            logger.error("Stripe signature verification failed", e);
            return false;
        }
    }

    private String computeHmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder result = new StringBuilder();
        for (byte b : hash) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    private void processPayPalEvent(String payload) {
        try {
            if (payload.contains("\"event_type\":\"PAYMENT.SALE.COMPLETED\"") ||
                payload.contains("\"event_type\":\"CHECKOUT.ORDER.APPROVED\"")) {

                String paymentId = extractJsonValue(payload, "id");
                if (paymentId != null) {
                    updateInvoiceStatus(paymentId, PaymentStatus.PAID);
                    logger.success("PayPal payment completed: %s", paymentId);
                }
            }
            else if (payload.contains("\"event_type\":\"PAYMENT.SALE.REFUNDED\"")) {
                String paymentId = extractJsonValue(payload, "parent_payment");
                if (paymentId != null) {
                    updateInvoiceStatus(paymentId, PaymentStatus.REFUNDED);
                    logger.payment("PayPal payment refunded: %s", paymentId);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to process PayPal event", e);
        }
    }

    private void processStripeEvent(String payload) {
        try {
            if (payload.contains("\"type\":\"payment_intent.succeeded\"")) {
                String paymentIntentId = extractJsonValue(payload, "id");
                if (paymentIntentId != null) {
                    updateInvoiceStatus(paymentIntentId, PaymentStatus.PAID);
                    logger.success("Stripe payment completed: %s", paymentIntentId);
                }
            }
            else if (payload.contains("\"type\":\"payment_intent.payment_failed\"")) {
                String paymentIntentId = extractJsonValue(payload, "id");
                if (paymentIntentId != null) {
                    updateInvoiceStatus(paymentIntentId, PaymentStatus.FAILED);
                    logger.payment("Stripe payment failed: %s", paymentIntentId);
                }
            }
            else if (payload.contains("\"type\":\"charge.dispute.created\"")) {
                String chargeId = extractJsonValue(payload, "id");
                if (chargeId != null) {
                    updateInvoiceStatus(chargeId, PaymentStatus.CANCELLED);
                    logger.payment("Stripe charge disputed: %s", chargeId);
                }
            }

        } catch (Exception e) {
            logger.error("Failed to process Stripe event", e);
        }
    }

    private void updateInvoiceStatus(String externalPaymentId, PaymentStatus status) {
        try {
            var dataManager = DatabaseManager.getDataMethods();
            if (dataManager != null) {
                var allInvoices = dataManager.getAllInvoices();
                for (Invoice invoice : allInvoices) {
                    if (externalPaymentId.equals(invoice.getExternalPaymentId())) {
                        PaymentStatus oldStatus = invoice.getStatus();
                        invoice.setStatus(status);
                        dataManager.updateInvoice(invoice);

                        logger.database("Updated invoice %s status from %s to %s", invoice.getInvoiceId(), oldStatus, status);

                        updateDiscordMessages(invoice, status);
                        return;
                    }
                }
                logger.warn("Invoice not found for external payment ID: %s", externalPaymentId);
            }
        } catch (Exception e) {
            logger.error("Failed to update invoice status", e);
        }
    }

    private void updateDiscordMessages(Invoice invoice, PaymentStatus status) {
        try {
            var jda = net.kyver.invoices.KyverInvoices.getJDA();
            if (jda == null) return;

            var user = jda.getUserById(invoice.getDiscordUserId());

            var channel = jda.getTextChannelById(invoice.getChannelId());

            switch (status) {
                case PAID -> {
                    if (channel != null && invoice.getChannelMessageId() != null) {
                        updateChannelMessageForPayment(channel, invoice);
                    }

                    if (channel != null) {
                        net.kyver.invoices.service.NotificationService.sendPaymentCompletedNotification(channel, invoice);
                    }

                    if (user != null) {
                        net.kyver.invoices.service.DMService.sendPaymentCompletedDM(user, invoice);
                    }

                    logger.success("Updated Discord messages for completed payment: %s", invoice.getInvoiceId());
                }

                case FAILED -> {
                    if (channel != null) {
                        net.kyver.invoices.service.NotificationService.sendPaymentFailedNotification(
                            channel, invoice, "Payment processing failed via webhook"
                        );
                    }

                    logger.warn("Notified about failed payment: %s", invoice.getInvoiceId());
                }

                case REFUNDED -> {
                    if (channel != null && invoice.getChannelMessageId() != null) {
                        updateChannelMessageStatus(channel, invoice);
                    }

                    logger.info("Updated messages for refunded payment: %s", invoice.getInvoiceId());
                }

                default -> {
                    if (channel != null && invoice.getChannelMessageId() != null) {
                        updateChannelMessageStatus(channel, invoice);
                    }
                }
            }

        } catch (Exception e) {
            logger.error("Failed to update Discord messages for invoice: " + invoice.getInvoiceId(), e);
        }
    }

    private void updateChannelMessageForPayment(net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel, Invoice invoice) {
        try {
            channel.retrieveMessageById(invoice.getChannelMessageId()).queue(message -> {
                var embed = net.kyver.invoices.manager.EmbedManager.custom(channel.getGuild())
                        .setColor(net.kyver.invoices.manager.EmbedManager.getSuccessColor())
                        .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8) + " - PAID ✅")
                        .setDescription("**" + invoice.getDescription() + "**")
                        .addField("Customer", invoice.getCustomerName(), true)
                        .addField("Amount", invoice.getFormattedAmount(), true)
                        .addField("Status", "✅ " + invoice.getStatus().toString(), true)
                        .addField("Payment Method", invoice.getSelectedGateway().toString(), true)
                        .addField("Completed", java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
                        ), true)
                        .build();

                message.editMessageEmbeds(embed).setComponents().queue();
            }, error -> logger.error("Failed to update channel message: " + error.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to update channel message for payment", e);
        }
    }

    private void updateChannelMessageStatus(net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel, Invoice invoice) {
        try {
            channel.retrieveMessageById(invoice.getChannelMessageId()).queue(message -> {
                var embed = net.kyver.invoices.manager.EmbedManager.custom(channel.getGuild())
                        .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                        .setDescription("**" + invoice.getDescription() + "**")
                        .addField("Customer", invoice.getCustomerName(), true)
                        .addField("Amount", invoice.getFormattedAmount(), true)
                        .addField("Status", getStatusEmoji(invoice.getStatus()) + " " + invoice.getStatus().toString(), true)
                        .addField("Updated", java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
                        ), true)
                        .build();

                message.editMessageEmbeds(embed).queue();
            }, error -> logger.error("Failed to update channel message status: " + error.getMessage()));

        } catch (Exception e) {
            logger.error("Failed to update channel message status", e);
        }
    }

    private String getStatusEmoji(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "⏳";
            case PAID -> "✅";
            case FAILED -> "❌";
            case CANCELLED -> "🚫";
            case REFUNDED -> "🔄";
            case EXPIRED -> "⏰";
            default -> "❓";
        };
    }

    private String extractJsonValue(String json, String key) {
        try {
            String searchKey = "\"" + key + "\":";
            int startIndex = json.indexOf(searchKey);
            if (startIndex == -1) return null;

            startIndex += searchKey.length();

            while (startIndex < json.length() &&
                   (json.charAt(startIndex) == ' ' || json.charAt(startIndex) == '"')) {
                startIndex++;
            }

            int endIndex = startIndex;
            while (endIndex < json.length() &&
                   json.charAt(endIndex) != '"' &&
                   json.charAt(endIndex) != ',' &&
                   json.charAt(endIndex) != '}') {
                endIndex++;
            }

            return json.substring(startIndex, endIndex);
        } catch (Exception e) {
            logger.debug("Failed to extract JSON value for key: %s", key);
            return null;
        }
    }

    private String readRequestBody(HttpExchange exchange) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }

    private Map<String, String> getHeaders(HttpExchange exchange) {
        Map<String, String> headers = new HashMap<>();
        exchange.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key.toLowerCase(), values.get(0));
            }
        });
        return headers;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, response.length());
        exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));
        exchange.getResponseBody().close();
    }
}
