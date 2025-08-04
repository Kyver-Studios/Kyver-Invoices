package net.kyver.invoices;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.LoggingManager;

public class KyverInvoices {

    private static final LoggingManager logger = LoggingManager.getLogger(KyverInvoices.class);
    private static JDA jda;
    private static DatabaseManager databaseManager;
    private static ConfigManager configManager;

    public static void main(String[] args) {
        logger.info("Starting Kyver Invoices Discord Bot...");

        try {
            logger.info("Loading configuration...");
            configManager = ConfigManager.getInstance();

            logger.info("Initializing database...");
            databaseManager = new DatabaseManager();
            databaseManager.initializeDatabase();

            logger.info("Connecting to Discord...");
            String token = System.getenv("DISCORD_TOKEN");
            if (token == null || token.isEmpty()) {
                logger.error("Discord token not found! Set DISCORD_TOKEN environment variable.");
                return;
            }

            jda = JDABuilder.createDefault(token)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("for invoices"))
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT
                    )
                    .build()
                    .awaitReady();

            logger.info("Kyver Invoices bot started successfully!");
            logger.info("Bot is ready and listening for commands.");

        } catch (Exception e) {
            logger.error("Failed to start bot", e);
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutting down Kyver Invoices bot...");
            if (jda != null) {
                jda.shutdown();
            }
            if (databaseManager != null) {
                try {
                    DatabaseManager.getDataMethods().close();
                } catch (Exception e) {
                    logger.error("Error closing database", e);
                }
            }
            logger.info("Bot shutdown complete.");
        }));
    }

    public static JDA getJDA() {
        return jda;
    }

    public static DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}