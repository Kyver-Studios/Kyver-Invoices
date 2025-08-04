package net.kyver.invoices.handler;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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

            paymentManager.createPaymentLink(invoice.getInvoiceId(), gateway).thenAccept(paymentUrl -> {
                invoice.setPaymentUrl(paymentUrl);

                try {
                    byte[] qrCodeData = QRCodeService.generateQRCode(paymentUrl);

                    DatabaseManager.getDataMethods().updateInvoice(invoice);

                    event.getHook().deleteOriginal().queue();

                    DMService.sendPaymentReadyDM(event.getUser(), invoice, qrCodeData);

                    TextChannel channel = event.getJDA().getTextChannelById(invoice.getChannelId());
                    if (channel != null) {
                        NotificationService.sendPaymentReadyNotification(channel, invoice);
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
            String invoiceIdStr = event.getComponentId().replace("resend-dm-", "");
            UUID invoiceId = UUID.fromString(invoiceIdStr);

            Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
            if (invoice == null) {
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            User user = event.getJDA().getUserById(invoice.getDiscordUserId());
            if (user == null) {
                event.reply("❌ User not found!").setEphemeral(true).queue();
                return;
            }

            if (invoice.getSelectedGateway() == null) {
                DMService.sendPaymentSelectionDM(user, invoice);
            } else if (invoice.getPaymentUrl() != null) {
                byte[] qrCodeData = QRCodeService.generateQRCode(invoice.getPaymentUrl());
                DMService.sendPaymentReadyDM(user, invoice, qrCodeData);
            }

            event.reply("✅ DM resent to " + user.getAsMention()).setEphemeral(true).queue();

        } catch (Exception e) {
            logger.error("Error resending DM", e);
            event.reply("❌ Failed to resend DM").setEphemeral(true).queue();
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
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            invoice.setStatus(PaymentStatus.CANCELLED);
            DatabaseManager.getDataMethods().updateInvoice(invoice);

            var embed = EmbedManager.custom(event.getGuild())
                    .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                    .setDescription("**" + invoice.getDescription() + "**")
                    .addField("Amount", invoice.getFormattedAmount(), true)
                    .addField("Status", getStatusEmoji(invoice.getStatus()) + " " + invoice.getStatus().toString(), true)
                    .build();

            event.getMessage().editMessageEmbeds(embed).setComponents().queue();

            User user = event.getJDA().getUserById(invoice.getDiscordUserId());
            if (user != null) {
                DMService.sendPaymentCancelledDM(user, invoice);
            }

            event.reply("❌ Invoice cancelled by admin").queue();

        } catch (Exception e) {
            logger.error("Error cancelling invoice", e);
            event.reply("❌ Failed to cancel invoice").setEphemeral(true).queue();
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
                event.reply("❌ Invoice not found!").setEphemeral(true).queue();
                return;
            }

            event.getMessage().delete().queue();

            TextChannel channel = event.getJDA().getTextChannelById(invoice.getChannelId());
            if (channel != null) {
                NotificationService.sendPaymentCancelledByUserNotification(channel, invoice, event.getUser());
            }

            DMService.sendPaymentCancelledDM(event.getUser(), invoice);

        } catch (Exception e) {
            logger.error("Error cancelling payment", e);
            event.reply("❌ Failed to cancel payment").setEphemeral(true).queue();
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

            TextChannel channel = event.getChannel().asTextChannel();
            channel.delete().reason("Invoice deleted by admin").queue();

        } catch (Exception e) {
            logger.error("Error deleting invoice", e);
            event.reply("❌ Failed to delete invoice").setEphemeral(true).queue();
        }
    }

    private net.dv8tion.jda.api.interactions.components.ActionRow createInvoiceChannelButtons(Invoice invoice) {
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
