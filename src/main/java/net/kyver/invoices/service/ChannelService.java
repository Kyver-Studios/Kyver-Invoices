package net.kyver.invoices.service;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.model.Invoice;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;

public class ChannelService {

    private static final LoggingManager logger = LoggingManager.getLogger(ChannelService.class);
    private static final ConfigManager config = ConfigManager.getInstance();

    public static CompletableFuture<TextChannel> createInvoiceChannel(Guild guild, Invoice invoice, User user) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String channelName = generateChannelName(invoice, user);
                Category category = getInvoiceCategory(guild);

                if (category == null) {
                    logger.error("Invoice category not found or invalid. Check config.yml");
                    throw new RuntimeException("Invoice category not configured properly");
                }

                TextChannel channel = category.createTextChannel(channelName)
                        .reason("Invoice channel for invoice #" + invoice.getInvoiceId().toString().substring(0, 8))
                        .complete();

                setupChannelPermissions(channel, user, guild);

                logger.info("Created invoice channel: " + channel.getName() + " for user: " + user.getEffectiveName());
                return channel;

            } catch (InsufficientPermissionException e) {
                logger.error("Bot lacks permissions to create invoice channel", e);
                throw new RuntimeException("Insufficient permissions to create channel", e);
            } catch (HierarchyException e) {
                logger.error("Bot role hierarchy issue when creating channel", e);
                throw new RuntimeException("Role hierarchy issue", e);
            } catch (Exception e) {
                logger.error("Failed to create invoice channel", e);
                throw new RuntimeException("Failed to create invoice channel", e);
            }
        });
    }

    private static String generateChannelName(Invoice invoice, User user) {
        String shortId = invoice.getInvoiceId().toString().substring(0, 8);
        String username = user.getName().toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .substring(0, Math.min(user.getName().length(), 15));

        return "invoice-" + shortId + "-" + username;
    }

    private static void setupChannelPermissions(TextChannel channel, User user, Guild guild) {
        try {
            channel.getManager().putPermissionOverride(
                guild.getPublicRole(),
                null,
                EnumSet.of(Permission.VIEW_CHANNEL)
            ).queue();

            Member member = guild.getMember(user);
            if (member != null) {
                channel.getManager().putPermissionOverride(
                        member,
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                        null
                ).queue();
            } else {
                logger.warn("Could not find member for user " + user.getName() + " in guild");
            }

            String adminRoleId = config.getAdminRoleId();
            if (adminRoleId != null && !adminRoleId.trim().isEmpty()) {
                Role adminRole = guild.getRoleById(adminRoleId);
                if (adminRole != null) {
                    channel.getManager().putPermissionOverride(
                        adminRole,
                        EnumSet.of(
                            Permission.VIEW_CHANNEL,
                            Permission.MESSAGE_SEND,
                            Permission.MESSAGE_HISTORY,
                            Permission.MANAGE_CHANNEL,
                            Permission.MESSAGE_MANAGE
                        ),
                        null
                    ).queue();
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to set up channel permissions: " + e.getMessage());
        }
    }

    private static Category getInvoiceCategory(Guild guild) {
        String categoryId = config.getInvoiceCategoryId();
        if (categoryId == null || categoryId.trim().isEmpty()) {
            logger.error("Invoice category ID not configured in config.yml");
            return null;
        }

        Category category = guild.getCategoryById(categoryId);
        if (category == null) {
            logger.error("Invoice category not found with ID: " + categoryId);
        }

        return category;
    }

    public static CompletableFuture<Void> deleteInvoiceChannel(TextChannel channel, String reason) {
        return CompletableFuture.runAsync(() -> {
            try {
                logger.info("Deleting invoice channel: " + channel.getName());
                channel.delete().reason(reason).queue();
            } catch (Exception e) {
                logger.error("Failed to delete invoice channel: " + channel.getName(), e);
            }
        });
    }
}

