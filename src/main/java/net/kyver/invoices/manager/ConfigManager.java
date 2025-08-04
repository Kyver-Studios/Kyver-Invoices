package net.kyver.invoices.manager;

import net.dv8tion.jda.api.entities.Guild;
import net.kyver.invoices.KyverInvoices;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ConfigManager {
    private static ConfigManager instance;
    private final Properties config;
    private static final LoggingManager logger = LoggingManager.getLogger(ConfigManager.class);

    private ConfigManager() {
        this.config = new Properties();
        loadConfig();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }

    private void loadConfig() {
        try {
            File configFile = new File("config.yml");
            if (!configFile.exists()) {
                configFile = new File(getClass().getClassLoader().getResource("config.yml").getFile());
            }

            if (configFile.exists()) {
                config.load(new FileInputStream(configFile));
                logger.info("Configuration loaded successfully");
            } else {
                logger.warn("Config file not found, using defaults");
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
        }
    }

    public boolean isStripeEnabled() {
        return Boolean.parseBoolean(config.getProperty("payment.stripe.enabled", "false"));
    }

    public boolean isPayPalEnabled() {
        return Boolean.parseBoolean(config.getProperty("payment.paypal.enabled", "false"));
    }

    public String getStripeApiKey() {
        return config.getProperty("payment.stripe.api_key", "");
    }

    public String getStripeSecretKey() {
        return config.getProperty("payment.stripe.secret_key", "");
    }

    public String getStripeWebhookSecret() {
        return config.getProperty("payment.stripe.webhook_secret", "");
    }

    public String getPayPalClientId() {
        return config.getProperty("payment.paypal.client_id", "");
    }

    public String getPayPalClientSecret() {
        return config.getProperty("payment.paypal.client_secret", "");
    }

    public String getPayPalMode() {
        return config.getProperty("payment.paypal.mode", "sandbox");
    }

    public String getWebApiUrl() {
        return config.getProperty("web_api.url", "http://localhost:8080");
    }

    public int getWebApiPort() {
        return Integer.parseInt(config.getProperty("web_api.port", "8080"));
    }

    public String getAdminRoleId() {
        return config.getProperty("discord.admin_role_id", "");
    }

    public String getUserRoleId() {
        return config.getProperty("discord.user_role_id", "");
    }

    public String getDefaultCurrency() {
        return config.getProperty("invoice.default_currency", "USD");
    }

    public int getDefaultDueDays() {
        return Integer.parseInt(config.getProperty("invoice.default_due_days", "30"));
    }

    public String getInvoiceCategoryId() {
        return config.getProperty("discord.invoice_category_id", "");
    }

    public String getDatabaseType() {
        return config.getProperty("database.type", "sqlite");
    }

    public String getString(String key, String defaultValue) {
        return config.getProperty(key, defaultValue);
    }

    public Color getMainColor() {
        String colorHex = config.getProperty("bot.main-color", "#E53935");
        return Color.decode(colorHex);
    }

    public Color getSuccessColor() {
        String colorHex = config.getProperty("bot.success-color", "#43A047");
        return Color.decode(colorHex);
    }

    public Color getErrorColor() {
        String colorHex = config.getProperty("bot.error-color", "#D32F2F");
        return Color.decode(colorHex);
    }

    public String getBotName() {
        return config.getProperty("bot.name", "KyverInvoices");
    }

    public Guild getGuild() {
        String guildId = config.getProperty("bot.guild_id", "YOUR_GUILD_ID");
        if (guildId.equals("YOUR_GUILD_ID") || guildId.isEmpty()) {
            logger.warn("Guild ID not set in config, returning null");
            return null;
        }
        return KyverInvoices.getJDA().getGuildById(guildId);
    }

    public void reload() {
        config.clear();
        loadConfig();
    }
}
