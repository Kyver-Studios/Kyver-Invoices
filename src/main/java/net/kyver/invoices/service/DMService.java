package net.kyver.invoices.service;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.EmbedManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.model.Invoice;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DMService {

    private static final LoggingManager logger = LoggingManager.getLogger(DMService.class);
    private static final ConfigManager config = ConfigManager.getInstance();

    public static CompletableFuture<Void> sendPaymentSelectionDM(User user, Invoice invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                user.openPrivateChannel().queue(privateChannel -> {
                    var embed = EmbedManager.custom()
                            .setTitle("💳 Select Payment Method")
                            .setDescription("Please select how you would like to pay for:\n**" + invoice.getDescription() + "**")
                            .addField("Amount", invoice.getFormattedAmount(), true)
                            .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                            .setFooter("Select a payment method from the dropdown below", null)
                            .build();

                    StringSelectMenu paymentMenu = createPaymentMethodMenu(invoice);

                    privateChannel.sendMessageEmbeds(embed)
                            .addComponents(ActionRow.of(paymentMenu))
                            .queue(
                                message -> {
                                    invoice.setDmSelectionMessageId(message.getId());
                                    DatabaseManager.getDataMethods().updateInvoice(invoice);
                                    logger.info("Sent payment selection DM to user: " + user.getEffectiveName());
                                },
                                error -> logger.error("Failed to send payment selection DM to user: " + user.getEffectiveName(), error)
                            );
                });
            } catch (Exception e) {
                logger.error("Failed to send payment selection DM", e);
            }
        });
    }

    public static CompletableFuture<Void> sendPaymentReadyDM(User user, Invoice invoice, byte[] qrCodeData) {
        return CompletableFuture.runAsync(() -> {
            try {
                user.openPrivateChannel().queue(privateChannel -> {
                    String gateway = invoice.getSelectedGateway() != null ? invoice.getSelectedGateway().toString() : "Unknown";

                    var embed = EmbedManager.custom()
                            .setTitle("🚀 Payment Ready - " + gateway)
                            .setDescription("Your payment is ready! You can pay using the QR code above or the link below.")
                            .addField("Amount", invoice.getFormattedAmount(), true)
                            .addField("Payment Method", gateway, true)
                            .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                            .setImage("attachment://qr-code.png")
                            .setFooter("Scan the QR code or click 'Pay Now' to complete your payment", null)
                            .build();

                    var buttons = createPaymentReadyButtons(invoice);

                    privateChannel.sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(qrCodeData, "qr-code.png"))
                            .setEmbeds(embed)
                            .addComponents(buttons)
                            .queue(
                                message -> {
                                    invoice.setDmPaymentMessageId(message.getId());
                                    DatabaseManager.getDataMethods().updateInvoice(invoice);
                                    logger.info("Sent payment ready DM to user: " + user.getEffectiveName());
                                },
                                error -> logger.error("Failed to send payment ready DM to user: " + user.getEffectiveName(), error)
                            );
                });
            } catch (Exception e) {
                logger.error("Failed to send payment ready DM", e);
            }
        });
    }

    public static CompletableFuture<Void> sendPaymentCompletedDM(User user, Invoice invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                user.openPrivateChannel().queue(privateChannel -> {
                    var embed = EmbedManager.custom()
                            .setColor(EmbedManager.getSuccessColor())
                            .setTitle("✅ Payment Completed!")
                            .setDescription("Thank you! Your payment has been successfully processed.")
                            .addField("Amount Paid", invoice.getFormattedAmount(), true)
                            .addField("Payment Method", invoice.getSelectedGateway().toString(), true)
                            .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                            .setFooter("You will receive a receipt shortly", null)
                            .build();

                    privateChannel.sendMessageEmbeds(embed)
                            .queue(
                                message -> logger.info("Sent payment completed DM to user: " + user.getEffectiveName()),
                                error -> logger.error("Failed to send payment completed DM to user: " + user.getEffectiveName(), error)
                            );
                });
            } catch (Exception e) {
                logger.error("Failed to send payment completed DM", e);
            }
        });
    }

    public static CompletableFuture<Void> sendPaymentCancelledDM(User user, Invoice invoice) {
        return CompletableFuture.runAsync(() -> {
            try {
                user.openPrivateChannel().queue(privateChannel -> {
                    var embed = EmbedManager.custom()
                            .setColor(EmbedManager.getErrorColor())
                            .setTitle("❌ Payment Cancelled")
                            .setDescription("Your payment has been cancelled.")
                            .addField("Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                            .addField("Amount", invoice.getFormattedAmount(), true)
                            .setFooter("Contact support if you need assistance", null)
                            .build();

                    privateChannel.sendMessageEmbeds(embed)
                            .queue(
                                message -> logger.info("Sent payment cancelled DM to user: " + user.getEffectiveName()),
                                error -> logger.error("Failed to send payment cancelled DM to user: " + user.getEffectiveName(), error)
                            );
                });
            } catch (Exception e) {
                logger.error("Failed to send payment cancelled DM", e);
            }
        });
    }

    private static ActionRow createPaymentReadyButtons(Invoice invoice) {
        return ActionRow.of(
                Button.link(
                        invoice.getPaymentUrl(),
                        "Pay Now"
                ).withEmoji(Emoji.fromUnicode("💳")),

                Button.secondary(
                        "need-help-" + invoice.getInvoiceId().toString(),
                        "Need Help"
                ).withEmoji(Emoji.fromUnicode("❓")),

                Button.danger(
                        "cancel-payment-" + invoice.getInvoiceId().toString(),
                        "Cancel"
                )
        );
    }

    private static StringSelectMenu createPaymentMethodMenu(Invoice invoice) {
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("payment-method-" + invoice.getInvoiceId().toString())
                .setPlaceholder("Select a payment method...")
                .setRequiredRange(1, 1);

        List<String> availableMethods = getEnabledPaymentMethods();

        for (String method : availableMethods) {
            String emoji = getPaymentMethodEmoji(method);
            menuBuilder.addOption(method, method.toLowerCase(), "Pay with " + method,
                    Emoji.fromUnicode(emoji));
        }

        if (availableMethods.isEmpty()) {
            menuBuilder.addOption("No payment methods available", "none", "Contact support");
            menuBuilder.setDisabled(true);
        }

        return menuBuilder.build();
    }

    private static List<String> getEnabledPaymentMethods() {
        List<String> methods = new ArrayList<>();

        if (config.isPayPalEnabled()) {
            methods.add("PayPal");
        }

        if (config.isStripeEnabled()) {
            methods.add("Stripe");
        }

        return methods;
    }

    private static String getPaymentMethodEmoji(String method) {
        return switch (method.toLowerCase()) {
            case "paypal" -> "🅿️";
            case "stripe" -> "💳";
            default -> "💰";
        };
    }
}
