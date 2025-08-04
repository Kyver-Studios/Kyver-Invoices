package net.kyver.invoices.service;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.EmbedManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.model.Invoice;

import java.util.concurrent.CompletableFuture;

public class NotificationService {

    private static final LoggingManager logger = LoggingManager.getLogger(NotificationService.class);
    private static final ConfigManager config = ConfigManager.getInstance();

    public static CompletableFuture<Void> sendPaymentReadyNotification(TextChannel channel, Invoice invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                var embed = EmbedManager.custom(channel.getGuild())
                        .setColor(EmbedManager.INFO_COLOR)
                        .setTitle("💳 Payment Link Generated")
                        .setDescription("Payment link has been sent to the user via DM.")
                        .addField("Payment Method", invoice.getSelectedGateway().toString(), true)
                        .addField("Amount", invoice.getFormattedAmount(), true)
                        .build();

                channel.sendMessageEmbeds(embed)
                        .queue(
                            message -> logger.info("Sent payment ready notification to channel: " + channel.getName()),
                            error -> logger.error("Failed to send payment ready notification", error)
                        );
            } catch (Exception e) {
                logger.error("Failed to send payment ready notification", e);
            }
        });
    }

    public static CompletableFuture<Void> sendPaymentCompletedNotification(TextChannel channel, Invoice invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                var embed = EmbedManager.custom(channel.getGuild())
                        .setColor(EmbedManager.getSuccessColor())
                        .setTitle("✅ Payment Received!")
                        .setDescription("The invoice has been paid successfully.")
                        .addField("Amount Received", invoice.getFormattedAmount(), true)
                        .addField("Payment Method", invoice.getSelectedGateway().toString(), true)
                        .setFooter("This invoice is now complete", null)
                        .build();

                String adminRoleId = config.getAdminRoleId();
                String mention = adminRoleId != null ? "<@&" + adminRoleId + ">" : "";

                channel.sendMessage(mention)
                        .setEmbeds(embed)
                        .queue(
                            message -> logger.info("Sent payment completed notification to channel: " + channel.getName()),
                            error -> logger.error("Failed to send payment completed notification", error)
                        );
            } catch (Exception e) {
                logger.error("Failed to send payment completed notification", e);
            }
        });
    }

    public static CompletableFuture<Void> sendPaymentCancelledByUserNotification(TextChannel channel, Invoice invoice, User user) {
        return CompletableFuture.runAsync(() -> {
            try {
                var embed = EmbedManager.custom(channel.getGuild())
                        .setColor(EmbedManager.WARNING_COLOR)
                        .setTitle("⚠️ Payment Cancelled by User")
                        .setDescription(user.getAsMention() + " has cancelled their payment.")
                        .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                        .addField("Amount", invoice.getFormattedAmount(), true)
                        .setFooter("Use the buttons below to recreate or delete this invoice", null)
                        .build();

                var buttons = createCancelledPaymentButtons(invoice);
                String adminRoleId = config.getAdminRoleId();
                String mention = adminRoleId != null ? "<@&" + adminRoleId + ">" : "";

                channel.sendMessage(mention)
                        .setEmbeds(embed)
                        .addComponents(buttons)
                        .queue(
                            message -> logger.info("Sent payment cancelled notification to channel: " + channel.getName()),
                            error -> logger.error("Failed to send payment cancelled notification", error)
                        );
            } catch (Exception e) {
                logger.error("Failed to send payment cancelled notification", e);
            }
        });
    }

    public static CompletableFuture<Void> sendPaymentFailedNotification(TextChannel channel, Invoice invoice, String reason) {
        return CompletableFuture.runAsync(() -> {
            try {
                var embed = EmbedManager.custom(channel.getGuild())
                        .setColor(EmbedManager.getErrorColor())
                        .setTitle("❌ Payment Failed")
                        .setDescription("The payment could not be processed.")
                        .addField("Reason", reason, false)
                        .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                        .addField("Amount", invoice.getFormattedAmount(), true)
                        .build();

                var buttons = createFailedPaymentButtons(invoice);
                String adminRoleId = config.getAdminRoleId();
                String mention = adminRoleId != null ? "<@&" + adminRoleId + ">" : "";

                channel.sendMessage(mention)
                        .setEmbeds(embed)
                        .addComponents(buttons)
                        .queue(
                            message -> logger.info("Sent payment failed notification to channel: " + channel.getName()),
                            error -> logger.error("Failed to send payment failed notification", error)
                        );
            } catch (Exception e) {
                logger.error("Failed to send payment failed notification", e);
            }
        });
    }

    public static CompletableFuture<Void> updateInvoiceChannelEmbed(TextChannel channel, Invoice invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                channel.getHistory().retrievePast(50).queue(messages -> {
                    messages.stream()
                            .filter(message -> message.getAuthor().equals(channel.getJDA().getSelfUser()))
                            .filter(message -> !message.getEmbeds().isEmpty())
                            .filter(message -> message.getEmbeds().get(0).getTitle() != null)
                            .filter(message -> message.getEmbeds().get(0).getTitle().contains("Invoice"))
                            .findFirst()
                            .ifPresent(message -> {
                                var embed = EmbedManager.custom(channel.getGuild())
                                        .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                                        .setDescription("**" + invoice.getDescription() + "**")
                                        .addField("Amount", invoice.getFormattedAmount(), true)
                                        .addField("Status", getStatusEmoji(invoice.getStatus()) + " " + invoice.getStatus().toString(), true)
                                        .build();

                                var buttons = createInvoiceChannelButtons(invoice);
                                message.editMessageEmbeds(embed)
                                        .setComponents(buttons)
                                        .queue();
                            });
                });
            } catch (Exception e) {
                logger.error("Failed to update invoice channel embed", e);
            }
        });
    }

    private static net.dv8tion.jda.api.interactions.components.ActionRow createCancelledPaymentButtons(Invoice invoice) {
        return net.dv8tion.jda.api.interactions.components.ActionRow.of(
                net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                        "recreate-invoice-" + invoice.getInvoiceId().toString(),
                        "Recreate Invoice"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔄")),

                net.dv8tion.jda.api.interactions.components.buttons.Button.danger(
                        "delete-invoice-" + invoice.getInvoiceId().toString(),
                        "Delete Invoice"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🗑️"))
        );
    }

    private static net.dv8tion.jda.api.interactions.components.ActionRow createFailedPaymentButtons(Invoice invoice) {
        return net.dv8tion.jda.api.interactions.components.ActionRow.of(
                net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                        "recreate-invoice-" + invoice.getInvoiceId().toString(),
                        "Retry Payment"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔄")),

                net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(
                        "resend-dm-" + invoice.getInvoiceId().toString(),
                        "Resend DM"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📧"))
        );
    }

    private static net.dv8tion.jda.api.interactions.components.ActionRow createInvoiceChannelButtons(Invoice invoice) {
        return net.dv8tion.jda.api.interactions.components.ActionRow.of(
                net.dv8tion.jda.api.interactions.components.buttons.Button.primary(
                        "resend-dm-" + invoice.getInvoiceId().toString(),
                        "Resend DM"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("📧")),

                net.dv8tion.jda.api.interactions.components.buttons.Button.secondary(
                        "refresh-status-" + invoice.getInvoiceId().toString(),
                        "Refresh Status"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🔄")),

                net.dv8tion.jda.api.interactions.components.buttons.Button.danger(
                        "cancel-invoice-" + invoice.getInvoiceId().toString(),
                        "Cancel"
                ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❌"))
        );
    }

    private static String getStatusEmoji(Enum<?> status) {
        String statusName = status.toString().toLowerCase();
        return switch (statusName) {
            case "pending" -> "⏳";
            case "paid", "completed", "success" -> "✅";
            case "failed", "declined" -> "❌";
            case "cancelled" -> "🚫";
            case "refunded" -> "🔄";
            case "expired" -> "⏰";
            default -> "❓";
        };
    }
}
