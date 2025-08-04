package net.kyver.invoices.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.kyver.invoices.KyverInvoices;
import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.EmbedManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.model.Invoice;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.service.ChannelService;
import net.kyver.invoices.service.DMService;

import java.math.BigDecimal;

public class InvoiceCommand extends ListenerAdapter {

    private final JDA jda;
    private final ConfigManager config;
    private static final LoggingManager logger = LoggingManager.getLogger(InvoiceCommand.class);

    public InvoiceCommand() {
        this.jda = KyverInvoices.getJDA();
        this.config = ConfigManager.getInstance();
        setupCommands();
    }

    private void setupCommands() {
        jda.updateCommands().addCommands(
                Commands.slash("invoice", "Invoice management commands")
                        .addSubcommands(
                                new SubcommandData("create", "Create a new invoice")
                                        .addOption(OptionType.USER, "user", "The user to create the invoice for", true)
                                        .addOption(OptionType.NUMBER, "amount", "Amount of the invoice", true)
                                        .addOption(OptionType.STRING, "description", "Description of the invoice", false)
                                        .addOption(OptionType.STRING, "item", "Item to be displayed on the invoice", false)
                        )
        ).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("invoice")) return;

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "create" -> handleCreateInvoice(event);
            default -> event.reply("Unknown subcommand!").setEphemeral(true).queue();
        }
    }

    private void handleCreateInvoice(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        try {
            User targetUser = event.getOption("user", OptionMapping::getAsUser);
            double amount = event.getOption("amount", OptionMapping::getAsDouble);
            String description = event.getOption("description", OptionMapping::getAsString);
            String item = event.getOption("item", OptionMapping::getAsString);

            if (amount <= 0) {
                event.getHook().editOriginal("❌ Amount must be greater than 0!")
                        .queue();
                return;
            }

            if (targetUser == null) {
                event.getHook().editOriginal("❌ Invalid user specified!")
                        .queue();
                return;
            }

            if (description == null || description.trim().isEmpty()) {
                description = item != null ? item : "Payment Request";
            }

            Invoice invoice = new Invoice();
            invoice.setDiscordUserId(targetUser.getId());
            invoice.setCustomerName(targetUser.getEffectiveName());
            invoice.setDescription(description.trim());
            invoice.setAmount(BigDecimal.valueOf(amount));
            invoice.setCurrency("USD");
            invoice.setStatus(PaymentStatus.PENDING);

            ChannelService.createInvoiceChannel(event.getGuild(), invoice, targetUser)
                    .thenAccept(channel -> {
                        invoice.setChannelId(channel.getId());

                        DatabaseManager.getDataMethods().createInvoice(invoice);

                        var channelEmbed = EmbedManager.custom(event.getGuild())
                                .setTitle("📧 Invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                                .setDescription("**" + invoice.getDescription() + "**")
                                .addField("Customer", invoice.getCustomerName(), true)
                                .addField("Amount", invoice.getFormattedAmount(), true)
                                .addField("Status", "⏳ " + invoice.getStatus().toString(), true)
                                .addField("Created", invoice.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm")), true)
                                .build();

                        var buttons = createInvoiceChannelButtons(invoice);

                        channel.sendMessageEmbeds(channelEmbed)
                                .addComponents(buttons)
                                .queue(message -> {
                                    invoice.setChannelMessageId(message.getId());
                                    DatabaseManager.getDataMethods().updateInvoice(invoice);
                                    logger.info("Stored channel message ID: " + message.getId());
                                });

                        DMService.sendPaymentSelectionDM(targetUser, invoice);

                        event.getHook().editOriginal("✅ Invoice created successfully! Channel: " + channel.getAsMention() +
                                "\nDM sent to " + targetUser.getAsMention() + " for payment method selection.")
                                .queue();

                    })
                    .exceptionally(throwable -> {
                        KyverInvoices.getLogger().error("Failed to create invoice channel", throwable);
                        event.getHook().editOriginal("❌ Failed to create invoice channel: " + throwable.getMessage())
                                .queue();
                        return null;
                    });

        } catch (Exception e) {
            KyverInvoices.getLogger().error("Failed to create invoice", e);
            event.getHook().editOriginal("❌ Failed to create invoice: " + e.getMessage())
                    .queue();
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
}
