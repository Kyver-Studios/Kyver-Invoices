package net.kyver.invoices.manager;

import net.dv8tion.jda.api.entities.Guild;
import net.kyver.invoices.KyverInvoices;
import org.yaml.snakeyaml.Yaml;

import java.awt.Color;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public class ConfigManager {
    private static ConfigManager instance;
    private Map<String, Object> config;
    private static final LoggingManager logger = LoggingManager.getLogger(ConfigManager.class);

    private ConfigManager() {
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
            Yaml yaml = new Yaml();
            InputStream inputStream;

            File configFile = new File("config.yml");
            if (configFile.exists()) {
                inputStream = new FileInputStream(configFile);
                logger.info("Loading configuration from external config.yml");
            } else {
                inputStream = getClass().getClassLoader().getResourceAsStream("config.yml");
                logger.info("Loading configuration from resource config.yml");
            }

            if (inputStream != null) {
                config = yaml.load(inputStream);
                inputStream.close();
                logger.info("Configuration loaded successfully");
            } else {
                logger.error("Config file not found");
                config = Map.of();
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration", e);
            config = Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Object getNestedValue(String path) {
        String[] keys = path.split("\\.");
        Object current = config;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    String getString(String path, String defaultValue) {
        Object value = getNestedValue(path);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean getBoolean(String path, boolean defaultValue) {
        Object value = getNestedValue(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value != null) {
            return Boolean.parseBoolean(value.toString());
        }
        return defaultValue;
    }

    private int getInt(String path, int defaultValue) {
        Object value = getNestedValue(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException e) {
                logger.warn("Invalid integer value for " + path + ": " + value);
            }
        }
        return defaultValue;
    }

    public boolean isStripeEnabled() {
        return getBoolean("gateways.stripe.enabled", false);
    }

    public boolean isPayPalEnabled() {
        return getBoolean("gateways.paypal.enabled", false);
    }

    public String getStripeApiKey() {
        return getString("gateways.stripe.public_key", "");
    }

    public String getStripeSecretKey() {
        return getString("gateways.stripe.secret_key", "");
    }

    public String getStripeWebhookSecret() {
        return getString("gateways.stripe.webhook_secret", "");
    }

    public String getPayPalClientId() {
        return getString("gateways.paypal.client_id", "");
    }

    public String getPayPalClientSecret() {
        return getString("gateways.paypal.client_secret", "");
    }

    public String getPayPalMode() {
        return getString("gateways.paypal.mode", "sandbox");
    }

    public String getWebApiUrl() {
        return getString("web_api.url", "http://localhost:3000");
    }

    public int getWebApiPort() {
        return getInt("web_api.port", 3000);
    }

    public String getJwtSecret() {
        return getString("web_api.auth.jwt_secret", "default_secret");
    }

    public String getBotToken() {
        return getString("bot.token", "");
    }

    public String getBotName() {
        return getString("bot.name", "KyverInvoices");
    }

    public String getGuildId() {
        return getString("bot.guild_id", "");
    }

    public String getInvoiceCategoryId() {
        return getString("bot.invoice-category", "");
    }

    public Color getMainColor() {
        String colorHex = getString("bot.main-color", "#E53935");
        return Color.decode(colorHex);
    }

    public Color getSuccessColor() {
        String colorHex = getString("bot.success-color", "#43A047");
        return Color.decode(colorHex);
    }

    public Color getErrorColor() {
        String colorHex = getString("bot.error-color", "#D32F2F");
        return Color.decode(colorHex);
    }

    public Guild getGuild() {
        String guildId = getGuildId();
        if (guildId.equals("YOUR_GUILD_ID") || guildId.isEmpty()) {
            logger.warn("Guild ID not set in config, returning null");
            return null;
        }
        return KyverInvoices.getJDA().getGuildById(guildId);
    }

    public String getAdminRoleId() {
        return getString("permissions.admin", "");
    }

    public String getUserRoleId() {
        return getString("permissions.user", "");
    }
}
