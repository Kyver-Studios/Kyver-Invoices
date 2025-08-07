package net.kyver.invoices.handler;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.enums.PaymentGateway;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.manager.EmbedManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.manager.PaymentManager;
import net.kyver.invoices.model.Invoice;
import net.kyver.invoices.service.DMService;
import net.kyver.invoices.service.NotificationService;
import net.kyver.invoices.service.QRCodeService;

import java.util.UUID;

public class ComponentHandler extends ListenerAdapter {

    private static final LoggingManager logger = LoggingManager.getLogger(ComponentHandler.class);
    private final PaymentManager paymentManager;

    public ComponentHandler(PaymentManager paymentManager) {
        this.paymentManager = paymentManager;
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("payment-method-")) {
            handlePaymentMethodSelection(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("resend-dm-")) {
            handleResendDM(event);
        } else if (componentId.startsWith("refresh-status-")) {
            handleRefreshStatus(event);
        } else if (componentId.startsWith("cancel-invoice-")) {
            handleCancelInvoice(event);
        } else if (componentId.startsWith("pay-now-")) {
            handlePayNow(event);
        } else if (componentId.startsWith("need-help-")) {
            handleNeedHelp(event);
        } else if (componentId.startsWith("cancel-payment-")) {
            handleCancelPayment(event);
        } else if (componentId.startsWith("recreate-invoice-")) {
            handleRecreateInvoice(event);
        } else if (componentId.startsWith("delete-invoice-")) {
            handleDeleteInvoice(event);
        } else if (componentId.startsWith("delete-channel-")) {
            handleDeleteChannel(event);
        }
    }

    private void handlePaymentMethodSelection(StringSelectInteractionEvent event) {
        try {
            String invoiceIdStr = event.getComponentId().replace("payment-method-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);
            String selectedMethod = event.getValues().get(0);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            event.deferReply(true).queue();

            PaymentGateway gateway = PaymentGateway.valueOf(selectedMethod.toUpperCase());
            invoice.setSelectedGateway(gateway);

            paymentManager.createPaymentLink(invoiceId, gateway).thenAccept(paymentUrl -> {
                invoice.setPaymentUrl(paymentUrl);

                try {
                    byte[] qrCodeData = QRCodeService.generateQRCode(paymentUrl);

                    DatabaseManager.getDataMethods().updateInvoice(invoice);

                    event.getHook().deleteOriginal().queue();

                    DMService.sendPaymentReadyDM(event.getUser(), invoice, qrCodeData);

                    if (invoice.getChannelId() != null && !invoice.getChannelId().isEmpty()) {
                        TextChannel channel = event.getJDA().getTextChannelById(invoice.getChannelId());
                        if (channel != null) {
                            NotificationService.sendPaymentReadyNotification(channel, invoice);
                        } else {
                            logger.warn("Channel not found for invoice: " + invoice.getChannelId());
                        }
                    } else {
                        logger.warn("No channel ID set for invoice: " + invoice.getInvoiceId());
                    }

                } catch (Exception e) {
                    logger.error("Failed to process payment method selection", e);
                    event.getHook().editOriginal("❌ Failed to create payment. Please try again.").queue();
                }
            }).exceptionally(throwable -> {
                logger.error("Failed to create payment link", throwable);
                event.getHook().editOriginal("❌ Failed to create payment link. Please contact support.").queue();
                return null;
            });

        } catch (Exception e) {
            logger.error("Error handling payment method selection", e);
            event.reply("❌ An error occurred. Please try again.").setEphemeral(true).queue();
        }
    }

    private void handleResendDM(ButtonInteractionEvent event) {
        try {
            event.deferReply(true).queue();

            String invoiceIdStr = event.getComponentId().replace("resend-dm-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                event.getHook().editOriginal("❌ Invoice not found!").queue();
                return;
            }

            User user = event.getJDA().getUserById(invoice.getDiscordUserId());
            if (user == null) {
                event.getHook().editOriginal("❌ User not found!").queue();
                return;
            }

            if (invoice.getSelectedGateway() == null) {
                DMService.sendPaymentSelectionDM(user, invoice).thenRun(() -> {
                    event.getHook().editOriginal("✅ DM resent to " + user.getAsMention()).queue();
                }).exceptionally(throwable -> {
                    logger.error("Failed to send DM", throwable);
                    event.getHook().editOriginal("❌ Failed to send DM").queue();
                    return null;
                });
            } else if (invoice.getPaymentUrl() != null) {
                try {
                    byte[] qrCodeData = QRCodeService.generateQRCode(invoice.getPaymentUrl());
                    DMService.sendPaymentReadyDM(user, invoice, qrCodeData).thenRun(() -> {
                        event.getHook().editOriginal("✅ DM resent to " + user.getAsMention()).queue();
                    }).exceptionally(throwable -> {
                        logger.error("Failed to send DM", throwable);
                        event.getHook().editOriginal("❌ Failed to send DM").queue();
                        return null;
                    });
                } catch (Exception e) {
                    logger.error("Failed to generate QR code", e);
                    event.getHook().editOriginal("❌ Failed to generate QR code").queue();
                }
            } else {
                event.getHook().editOriginal("❌ No payment information available to resend").queue();
            }

        } catch (Exception e) {
            logger.error("Error resending DM", e);
            if (!event.isAcknowledged()) {
                event.reply("❌ Failed to resend DM").setEphemeral(true).queue();
            } else {
                event.getHook().editOriginal("❌ Failed to resend DM").queue();
            }
        }
    }

    private void handleRefreshStatus(ButtonInteractionEvent event) {
        try {
            String invoiceIdStr = event.getComponentId().replace("refresh-status-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            event.deferReply(true).queue();

            if (invoice.getExternalPaymentId() != null) {
                paymentManager.checkPaymentStatus(invoice.getInvoiceId()).thenAccept(status -> {
                    invoice.setStatus(status);
                    DatabaseManager.getDataMethods().updateInvoice(invoice);

                    var embed = EmbedManager.custom(event.getGuild())
                            .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                            .setDescription("**" + invoice.getDescription() + "**")
                            .addField("Amount", invoice.getFormattedAmount(), true)
                            .addField("Status", getStatusEmoji(invoice.getStatus()) + " " + invoice.getStatus().toString(), true)
                            .build();

                    event.getMessage().editMessageEmbeds(embed).queue();
                    event.getHook().editOriginal("✅ Status refreshed: " + status.toString()).queue();
                }).exceptionally(throwable -> {
                    logger.error("Failed to check payment status", throwable);
                    event.getHook().editOriginal("❌ Failed to refresh status").queue();
                    return null;
                });
            } else {
                event.getHook().editOriginal("ℹ️ No payment to check status for").queue();
            }

        } catch (Exception e) {
            logger.error("Error refreshing status", e);
            event.reply("❌ Failed to refresh status").setEphemeral(true).queue();
        }
    }

    private void handleCancelInvoice(ButtonInteractionEvent event) {
        try {
            String invoiceIdStr = event.getComponentId().replace("cancel-invoice-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                var errorEmbed = EmbedManager.custom(event.getGuild())
                        .setColor(EmbedManager.getErrorColor())
                        .setTitle("❌ Invoice Not Found")
                        .setDescription("The invoice could not be found in the database.")
                        .build();
                event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
                return;
            }

            invoice.setStatus(PaymentStatus.CANCELLED);
            DatabaseManager.getDataMethods().updateInvoice(invoice);

            var embed = EmbedManager.custom(event.getGuild())
                    .setColor(EmbedManager.getErrorColor())
                    .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8) + " - CANCELLED")
                    .setDescription("**" + invoice.getDescription() + "**")
                    .addField("Customer", invoice.getCustomerName(), true)
                    .addField("Amount", invoice.getFormattedAmount(), true)
                    .addField("Status", "🚫 " + invoice.getStatus().toString(), true)
                    .addField("Cancelled By", event.getUser().getAsMention(), true)
                    .addField("Cancelled", java.time.LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")
                    ), true)
                    .build();

            var cancelledButtons = ActionRow.of(
                    Button.danger(
                            "delete-channel-" + invoice.getInvoiceId().toString(),
                            "Delete Channel"
                    ).withEmoji(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🗑️"))
            );

            event.getMessage().editMessageEmbeds(embed).setComponents(cancelledButtons).queue();

            User user = event.getJDA().getUserById(invoice.getDiscordUserId());
            if (user != null) {
                DMService.sendPaymentCancelledDM(user, invoice);
            }

            var successEmbed = EmbedManager.custom(event.getGuild())
                    .setColor(EmbedManager.getErrorColor())
                    .setTitle("🚫 Invoice Cancelled")
                    .setDescription("Invoice has been cancelled by admin. User has been notified.")
                    .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                    .addField("Customer", invoice.getCustomerName(), true)
                    .build();
            event.replyEmbeds(successEmbed).setEphemeral(true).queue();

        } catch (Exception e) {
            logger.error("Error cancelling invoice", e);
            var errorEmbed = EmbedManager.custom(event.getGuild())
                    .setColor(EmbedManager.getErrorColor())
                    .setTitle("❌ Cancellation Failed")
                    .setDescription("Failed to cancel invoice: " + e.getMessage())
                    .build();
            event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
        }
    }

    private void handlePayNow(ButtonInteractionEvent event) {
        event.reply("🔗 Use the payment link above to complete your payment.").setEphemeral(true).queue();
    }

    private void handleNeedHelp(ButtonInteractionEvent event) {
        var helpEmbed = EmbedManager.custom()
                .setColor(EmbedManager.INFO_COLOR)
                .setTitle("💡 Need Help?")
                .setDescription("Having trouble with your payment? Here's what you can do:")
                .addField("🔄 Try Again", "If the payment failed, you can try again with the same or different payment method.", false)
                .addField("📱 QR Code", "Use your phone's camera or a QR code scanner app to scan the code above.", false)
                .addField("💬 Contact Support", "If you're still having issues, please contact our support team.", false)
                .addField("🔗 Direct Link", "You can also click the 'Pay Now' button to open the payment page directly.", false)
                .setFooter("We're here to help!", null)
                .build();

        event.replyEmbeds(helpEmbed).setEphemeral(true).queue();
    }

    private void handleCancelPayment(ButtonInteractionEvent event) {
        try {
            String invoiceIdStr = event.getComponentId().replace("cancel-payment-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                var errorEmbed = EmbedManager.custom()
                        .setColor(EmbedManager.getErrorColor())
                        .setTitle("❌ Invoice Not Found")
                        .setDescription("The invoice could not be found in the database.")
                        .build();
                event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
                return;
            }

            event.reply("🚫 Payment cancelled successfully.").setEphemeral(true).queue();

            invoice.setStatus(PaymentStatus.CANCELLED);
            DatabaseManager.getDataMethods().updateInvoice(invoice);

            event.getMessage().delete().queue();

            if (invoice.getChannelId() != null && !invoice.getChannelId().isEmpty()) {
                TextChannel channel = event.getJDA().getTextChannelById(invoice.getChannelId());
                if (channel != null) {
                    NotificationService.sendPaymentCancelledByUserNotification(channel, invoice, event.getUser());
                } else {
                    logger.warn("Channel not found for invoice: " + invoice.getChannelId());
                }
            } else {
                logger.warn("No channel ID set for invoice: " + invoice.getInvoiceId());
            }

            DMService.sendPaymentCancelledDM(event.getUser(), invoice);

        } catch (Exception e) {
            logger.error("Error cancelling payment", e);
            if (!event.isAcknowledged()) {
                var errorEmbed = EmbedManager.custom()
                        .setColor(EmbedManager.getErrorColor())
                        .setTitle("❌ Cancellation Failed")
                        .setDescription("Failed to cancel payment. Please try again or contact support.")
                        .build();
                event.replyEmbeds(errorEmbed).setEphemeral(true).queue();
            }
        }
    }

    private void handleRecreateInvoice(ButtonInteractionEvent event) {
        try {
            String invoiceIdStr = event.getComponentId().replace("recreate-invoice-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            invoice.setStatus(PaymentStatus.PENDING);
            invoice.setSelectedGateway(null);
            invoice.setPaymentUrl(null);
            invoice.setExternalPaymentId(null);
            DatabaseManager.getDataMethods().updateInvoice(invoice);

            User user = event.getJDA().getUserById(invoice.getDiscordUserId());
            if (user != null) {
                DMService.sendPaymentSelectionDM(user, invoice);
            }

            var embed = EmbedManager.custom(event.getGuild())
                    .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                    .setDescription("**" + invoice.getDescription() + "**")
                    .addField("Amount", invoice.getFormattedAmount(), true)
                    .addField("Status", getStatusEmoji(invoice.getStatus()) + " " + invoice.getStatus().toString(), true)
                    .build();

            var buttons = createInvoiceChannelButtons(invoice);
            event.getMessage().editMessageEmbeds(embed).setComponents(buttons).queue();

            event.reply("✅ Invoice recreated and DM sent to user").setEphemeral(true).queue();

        } catch (Exception e) {
            logger.error("Error recreating invoice", e);
            event.reply("❌ Failed to recreate invoice").setEphemeral(true).queue();
        }
    }

    private void handleDeleteInvoice(ButtonInteractionEvent event) {
        try {
            String invoiceIdStr = event.getComponentId().replace("delete-invoice-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            DatabaseManager.getDataMethods().deleteInvoice(invoiceId);

            if (event.getChannel().getType().isGuild()) {
                TextChannel channel = event.getChannel().asTextChannel();
                channel.delete().reason("Invoice deleted by admin").queue();
            } else {
                event.reply("✅ Invoice deleted. (This was a DM, channel not deleted.)").setEphemeral(true).queue();
            }

        } catch (Exception e) {
            logger.error("Error deleting invoice", e);
            event.reply("❌ Failed to delete invoice").setEphemeral(true).queue();
        }
    }

    private void handleDeleteChannel(ButtonInteractionEvent event) {
        try {
            event.getChannel().delete().queue();
        } catch (Exception e) {
            logger.error("Error deleting channel", e);
            event.reply("❌ Failed to delete channel").setEphemeral(true).queue();
        }
    }

    private ActionRow createInvoiceChannelButtons(Invoice invoice) {
        return ActionRow.of(
                Button.primary(
                        "resend-dm-" + invoice.getInvoiceId().toString(),
                        "Resend DM"
                ).withEmoji(Emoji.fromUnicode("📧")),

                Button.secondary(
                        "refresh-status-" + invoice.getInvoiceId().toString(),
                        "Refresh Status"
                ).withEmoji(Emoji.fromUnicode("🔄")),

                Button.danger(
                        "cancel-invoice-" + invoice.getInvoiceId().toString(),
                        "Cancel"
                ).withEmoji(Emoji.fromUnicode("❌"))
        );
    }

    private String getStatusEmoji(Enum<?> status) {
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
