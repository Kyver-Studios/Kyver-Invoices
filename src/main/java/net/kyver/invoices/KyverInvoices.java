package net.kyver.invoices;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.kyver.invoices.command.InvoiceCommand;
import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.handler.ComponentHandler;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.LoggingManager;
import net.kyver.invoices.manager.PaymentManager;
import net.kyver.invoices.manager.WebApiManager;

public class KyverInvoices {

    private static final LoggingManager logger = LoggingManager.getLogger(KyverInvoices.class);

    private static JDA jda;
    private static DatabaseManager databaseManager;
    private static ConfigManager configManager;
    private static PaymentManager paymentManager;
    private static WebApiManager webApiManager;
    private static ComponentHandler componentHandler;
    private static InvoiceCommand invoiceCommand;

    private static KyverInvoices instance;

    public static void main(String[] args) {
        logger.info("Starting Kyver Invoices Discord Bot...");
        instance = new KyverInvoices();

        try {
            logger.info("Loading configuration...");
            configManager = ConfigManager.getInstance();
            validateConfiguration();

            logger.info("Initializing database...");
            databaseManager = new DatabaseManager();
            databaseManager.initializeDatabase();

            logger.info("Initializing payment gateways...");
            paymentManager = PaymentManager.getInstance();

            logger.info("Connecting to Discord...");
            String token = System.getenv("DISCORD_TOKEN");
            if (token == null || token.isEmpty()) {
                logger.error("Discord token not found! Set DISCORD_TOKEN environment variable.");
                return;
            }

            jda = JDABuilder.createDefault(token)
                    .setStatus(OnlineStatus.ONLINE)
                    .setActivity(Activity.watching("for invoice payments"))
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.DIRECT_MESSAGES,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.GUILD_MEMBERS
                    )
                    .build()
                    .awaitReady();

            logger.info("Registering Discord components...");
            registerDiscordComponents();

            logger.info("Starting web API for webhook handling...");
            webApiManager = new WebApiManager(configManager);
            webApiManager.startServer();

            validateGuildConfiguration();

            logger.success("✅ Kyver Invoices bot started successfully!");
            logger.info("🚀 Bot is ready and listening for commands.");
            logger.info("📧 Invoice system with channel creation and QR codes is active.");

        } catch (Exception e) {
            logger.error("❌ Failed to start bot", e);
            System.exit(1);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("🔄 Shutting down Kyver Invoices bot...");

            if (webApiManager != null) {
                try {
                    webApiManager.stopServer();
                    logger.info("✅ Web API stopped");
                } catch (Exception e) {
                    logger.error("❌ Error stopping web API", e);
                }
            }

            if (jda != null) {
                jda.shutdown();
                logger.info("✅ Discord connection closed");
            }

            if (databaseManager != null) {
                try {
                    DatabaseManager.getDataMethods().close();
                    logger.info("✅ Database connection closed");
                } catch (Exception e) {
                    logger.error("❌ Error closing database", e);
                }
            }

            logger.info("✅ Bot shutdown complete.");
        }));
    }

    private static void registerDiscordComponents() {
        try {
            invoiceCommand = new InvoiceCommand();
            jda.addEventListener(invoiceCommand);
            logger.success("✅ Invoice command registered");

            componentHandler = new ComponentHandler(paymentManager);
            jda.addEventListener(componentHandler);
            logger.success("✅ Component handler registered");

            logger.info("📋 All Discord components registered successfully");

        } catch (Exception e) {
            logger.error("❌ Failed to register Discord components", e);
            throw new RuntimeException("Component registration failed", e);
        }
    }

    private static void validateConfiguration() {
        logger.info("🔍 Validating configuration...");

        boolean hasErrors = false;

        String categoryId = configManager.getInvoiceCategoryId();
        if (categoryId == null || categoryId.trim().isEmpty() || "INVOICE_CATEGORY_ID".equals(categoryId)) {
            logger.error("❌ Invoice category ID not configured in config.yml");
            hasErrors = true;
        }

        String adminRole = configManager.getAdminRoleId();
        if (adminRole == null || adminRole.trim().isEmpty() || "ADMIN_ROLE_ID".equals(adminRole)) {
            logger.warn("⚠️ Admin role ID not configured - admin features may not work properly");
        }

        boolean hasPaymentGateway = false;
        if (configManager.isStripeEnabled()) {
            if (configManager.getStripeSecretKey() == null || configManager.getStripeSecretKey().startsWith("sk_test_YOUR")) {
                logger.error("❌ Stripe is enabled but secret key is not configured");
                hasErrors = true;
            } else {
                hasPaymentGateway = true;
                logger.success("✅ Stripe configuration validated");
            }
        }

        if (configManager.isPayPalEnabled()) {
            if (configManager.getPayPalClientId() == null || configManager.getPayPalClientId().startsWith("YOUR_PAYPAL")) {
                logger.error("❌ PayPal is enabled but client credentials are not configured");
                hasErrors = true;
            } else {
                hasPaymentGateway = true;
                logger.success("✅ PayPal configuration validated");
            }
        }

        if (!hasPaymentGateway) {
            logger.error("❌ No payment gateways are properly configured!");
            hasErrors = true;
        }

        if (configManager.getWebApiPort() <= 0 || configManager.getWebApiPort() > 65535) {
            logger.error("❌ Invalid web API port configuration");
            hasErrors = true;
        }

        if (hasErrors) {
            logger.error("❌ Configuration validation failed! Please check config.yml");
            throw new RuntimeException("Invalid configuration");
        }

        logger.success("✅ Configuration validation passed");
    }

    private static void validateGuildConfiguration() {
        try {
            Guild guild = configManager.getGuild();
            if (guild == null) {
                logger.warn("⚠️ Guild not found or not configured properly");
                return;
            }

            logger.info("🏰 Validating guild: " + guild.getName());

            String categoryId = configManager.getInvoiceCategoryId();
            if (guild.getCategoryById(categoryId) == null) {
                logger.error("❌ Invoice category not found in guild: " + categoryId);
                logger.error("💡 Please create a category and update the invoice-category ID in config.yml");
            } else {
                logger.success("✅ Invoice category found and accessible");
            }

            String adminRoleId = configManager.getAdminRoleId();
            if (adminRoleId != null && !adminRoleId.trim().isEmpty() && guild.getRoleById(adminRoleId) == null) {
                logger.warn("⚠️ Admin role not found in guild: " + adminRoleId);
            } else if (adminRoleId != null && !adminRoleId.trim().isEmpty()) {
                logger.success("✅ Admin role found and accessible");
            }

            if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MANAGE_CHANNEL)) {
                logger.warn("⚠️ Bot lacks MANAGE_CHANNEL permission - channel creation may fail");
            }

            if (!guild.getSelfMember().hasPermission(net.dv8tion.jda.api.Permission.MESSAGE_SEND)) {
                logger.error("❌ Bot lacks MESSAGE_SEND permission - bot will not function");
            }

            logger.success("✅ Guild configuration validated");

        } catch (Exception e) {
            logger.error("❌ Guild validation failed", e);
        }
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

    public static PaymentManager getPaymentManager() {
        return paymentManager;
    }

    public static LoggingManager getLogger() {
        return logger;
    }
}