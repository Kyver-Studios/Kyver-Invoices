package net.kyver.invoices.manager;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.kyver.invoices.model.Invoice;
import net.kyver.invoices.model.Payment;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class EmbedManager {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm");
    private static final ConfigManager config = ConfigManager.getInstance();

    public static Color getSuccessColor() {
        return config.getSuccessColor();
    }

    public static Color getErrorColor() {
        return config.getErrorColor();
    }

    public static Color getMainColor() {
        return config.getMainColor();
    }

    public static final Color WARNING_COLOR = Color.ORANGE;
    public static final Color INFO_COLOR = Color.CYAN;

    public static EmbedBuilder createBuilder() {
        return createBuilder(null);
    }

    public static EmbedBuilder createBuilder(Guild guild) {
        EmbedBuilder builder = new EmbedBuilder()
                .setColor(getMainColor())
                .setTimestamp(java.time.Instant.now());

        String botName = config.getBotName();
        if (guild != null) {
            String serverIcon = guild.getIconUrl();
            builder.setFooter(botName, serverIcon);
        } else {
            builder.setFooter(botName, null);
        }

        return builder;
    }

    public static MessageEmbed createSuccessEmbed(String title, String description) {
        return createSuccessEmbed(title, description, null);
    }

    public static MessageEmbed createSuccessEmbed(String title, String description, Guild guild) {
        return createBuilder(guild)
                .setColor(getSuccessColor())
                .setTitle("✅ " + title)
                .setDescription(description)
                .build();
    }

    public static MessageEmbed createErrorEmbed(String title, String description) {
        return createErrorEmbed(title, description, null);
    }

    public static MessageEmbed createErrorEmbed(String title, String description, Guild guild) {
        return createBuilder(guild)
                .setColor(getErrorColor())
                .setTitle("❌ " + title)
                .setDescription(description)
                .build();
    }

    public static MessageEmbed createWarningEmbed(String title, String description) {
        return createWarningEmbed(title, description, null);
    }

    public static MessageEmbed createWarningEmbed(String title, String description, Guild guild) {
        return createBuilder(guild)
                .setColor(WARNING_COLOR)
                .setTitle("⚠️ " + title)
                .setDescription(description)
                .build();
    }

    public static MessageEmbed createInfoEmbed(String title, String description) {
        return createInfoEmbed(title, description, null);
    }

    public static MessageEmbed createInfoEmbed(String title, String description, Guild guild) {
        return createBuilder(guild)
                .setColor(INFO_COLOR)
                .setTitle("ℹ️ " + title)
                .setDescription(description)
                .build();
    }

    public static MessageEmbed createInvoiceEmbed(Invoice invoice) {
        return createInvoiceEmbed(invoice, null);
    }

    public static MessageEmbed createInvoiceEmbed(Invoice invoice, Guild guild) {
        EmbedBuilder builder = createBuilder(guild)
                .setColor(getMainColor())
                .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                .setDescription("**" + invoice.getDescription() + "**");

        if (invoice.getCustomerName() != null) {
            builder.addField("Customer", invoice.getCustomerName(), true);
        }
        if (invoice.getCustomerEmail() != null) {
            builder.addField("Email", invoice.getCustomerEmail(), true);
        }

        builder.addField("Amount",
                String.format("%s %.2f", invoice.getCurrency(), invoice.getAmount()), true);
        builder.addField("Status",
                getStatusEmoji(invoice.getStatus()) + " " + invoice.getStatus().toString(), true);

        if (invoice.getSelectedGateway() != null) {
            builder.addField("Payment Gateway", invoice.getSelectedGateway().toString(), true);
        }

        builder.addField("Created",
                invoice.getCreatedAt().format(DATE_FORMAT), true);

        if (invoice.getDueDate() != null) {
            builder.addField("Due Date",
                    invoice.getDueDate().format(DATE_FORMAT), true);
        }

        if (invoice.getPaymentUrl() != null) {
            builder.addField("Payment Link", "[Click here to pay](" + invoice.getPaymentUrl() + ")", false);
        }

        return builder.build();
    }

    public static MessageEmbed createPaymentEmbed(Payment payment) {
        return createPaymentEmbed(payment, null);
    }

    public static MessageEmbed createPaymentEmbed(Payment payment, Guild guild) {
        return createBuilder(guild)
                .setColor(getSuccessColor())
                .setTitle("💳 Payment Processed")
                .setDescription("Payment has been successfully processed!")
                .addField("Payment ID", payment.getPaymentId().toString().substring(0, 8), true)
                .addField("Amount",
                        String.format("%s %.2f", payment.getCurrency(), payment.getAmount()), true)
                .addField("Gateway", payment.getGateway().toString(), true)
                .addField("Status",
                        getStatusEmoji(payment.getStatus()) + " " + payment.getStatus().toString(), true)
                .addField("Processed", payment.getCreatedAt().format(DATE_FORMAT), true)
                .build();
    }

    public static class CustomEmbedBuilder {
        private final EmbedBuilder builder;
        private final List<MessageEmbed.Field> fields;

        public CustomEmbedBuilder() {
            this(null);
        }

        public CustomEmbedBuilder(Guild guild) {
            this.builder = createBuilder(guild);
            this.fields = new ArrayList<>();
        }

        public CustomEmbedBuilder setTitle(String title) {
            builder.setTitle(title);
            return this;
        }

        public CustomEmbedBuilder setDescription(String description) {
            builder.setDescription(description);
            return this;
        }

        public CustomEmbedBuilder setColor(Color color) {
            builder.setColor(color);
            return this;
        }

        public CustomEmbedBuilder setThumbnail(String url) {
            builder.setThumbnail(url);
            return this;
        }

        public CustomEmbedBuilder setImage(String url) {
            builder.setImage(url);
            return this;
        }

        public CustomEmbedBuilder setAuthor(String name, String url, String iconUrl) {
            builder.setAuthor(name, url, iconUrl);
            return this;
        }

        public CustomEmbedBuilder setFooter(String text, String iconUrl) {
            builder.setFooter(text, iconUrl);
            return this;
        }

        public CustomEmbedBuilder addField(String name, String value, boolean inline) {
            builder.addField(name, value, inline);
            return this;
        }

        public CustomEmbedBuilder addField(String name, String value) {
            return addField(name, value, false);
        }

        public CustomEmbedBuilder addBlankField(boolean inline) {
            builder.addBlankField(inline);
            return this;
        }

        public CustomEmbedBuilder addInlineField(String name, String value) {
            return addField(name, value, true);
        }

        public MessageEmbed build() {
            return builder.build();
        }
    }

    public static CustomEmbedBuilder custom() {
        return new CustomEmbedBuilder();
    }

    public static CustomEmbedBuilder custom(Guild guild) {
        return new CustomEmbedBuilder(guild);
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
