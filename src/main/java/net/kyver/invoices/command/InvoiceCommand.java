package net.kyver.invoices.command;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
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

        if (!hasPermission(event)) {
            var errorEmbed = EmbedManager.custom(event.getGuild())
                    .setColor(EmbedManager.getErrorColor())
                    .setTitle("❌ Access Denied")
                    .setDescription("You don't have permission to use this command.")
                    .build();
            event.reply("").addEmbeds(errorEmbed).setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        switch (subcommand) {
            case "create" -> handleCreateInvoice(event);
            default -> event.reply("Unknown subcommand!").setEphemeral(true).queue();
        }
    }

    private boolean hasPermission(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null || event.getMember() == null) {
            return false;
        }

        Member member = event.getMember();
        String adminRoleId = config.getAdminRoleId();

        if (adminRoleId.isEmpty() || adminRoleId.equals("ADMIN_ROLE_ID")) {
            logger.warn("Admin role ID not configured properly in config.yml");
            return false;
        }

        Role adminRole = event.getGuild().getRoleById(adminRoleId);
        if (adminRole != null && member.getRoles().contains(adminRole)) {
            return true;
        }

        return false;
    }

    private void handleCreateInvoice(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        try {
            User targetUser = event.getOption("user", OptionMapping::getAsUser);
            double amount = event.getOption("amount", OptionMapping::getAsDouble);
            String description = event.getOption("description", OptionMapping::getAsString);
            String item = event.getOption("item", OptionMapping::getAsString);

            if (amount <= 0) {
                var errorEmbed = EmbedManager.custom(event.getGuild())
                        .setColor(EmbedManager.getErrorColor())
                        .setTitle("❌ Invalid Amount")
                        .setDescription("Amount must be greater than 0!")
                        .build();
                event.getHook().editOriginalEmbeds(errorEmbed).queue();
                return;
            }

            if (targetUser == null) {
                var errorEmbed = EmbedManager.custom(event.getGuild())
                        .setColor(EmbedManager.getErrorColor())
                        .setTitle("❌ Invalid User")
                        .setDescription("Please specify a valid user!")
                        .build();
                event.getHook().editOriginalEmbeds(errorEmbed).queue();
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

                        var successEmbed = EmbedManager.custom(event.getGuild())
                                .setColor(EmbedManager.getSuccessColor())
                                .setTitle("✅ Invoice Created Successfully!")
                                .setDescription("Your invoice has been created and the payment process has been initiated.")
                                .addField("📧 Invoice ID", "#" + invoice.getInvoiceId().toString().substring(0, 8), true)
                                .addField("👤 Customer", targetUser.getAsMention(), true)
                                .addField("💰 Amount", invoice.getFormattedAmount(), true)
                                .addField("📝 Description", invoice.getDescription(), false)
                                .addField("🏢 Channel", channel.getAsMention(), true)
                                .addField("📱 Next Steps", "The customer will receive a DM to select their payment method", false)
                                .setFooter("Use the channel buttons to manage this invoice", null)
                                .build();

                        event.getHook().editOriginalEmbeds(successEmbed).queue();

                    })
                    .exceptionally(throwable -> {
                        logger.error("Failed to create invoice channel", throwable);
                        var errorEmbed = EmbedManager.custom(event.getGuild())
                                .setColor(EmbedManager.getErrorColor())
                                .setTitle("❌ Channel Creation Failed")
                                .setDescription("Failed to create invoice channel: " + throwable.getMessage())
                                .build();
                        event.getHook().editOriginalEmbeds(errorEmbed).queue();
                        return null;
                    });

        } catch (Exception e) {
            logger.error("Failed to create invoice", e);
            var errorEmbed = EmbedManager.custom(event.getGuild())
                    .setColor(EmbedManager.getErrorColor())
                    .setTitle("❌ Invoice Creation Failed")
                    .setDescription("Failed to create invoice: " + e.getMessage())
                    .build();
            event.getHook().editOriginalEmbeds(errorEmbed).queue();
        }
    }

    private ActionRow createInvoiceChannelButtons(Invoice invoice) {
        return ActionRow.of(
                Button.primary(
                        "resend-dm-" + invoice.getInvoiceId().toString(),
                        "Send DM"
                ),

                Button.secondary(
                        "refresh-status-" + invoice.getInvoiceId().toString(),
                        "Refresh"
                ),

                Button.danger(
                        "cancel-invoice-" + invoice.getInvoiceId().toString(),
                        "Cancel"
                )
        );
    }
}
