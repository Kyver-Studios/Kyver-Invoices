package net.kyver.invoices.data;

import net.kyver.invoices.data.storage.SQLiteStorage;
import net.kyver.invoices.manager.ConfigManager;
import net.kyver.invoices.manager.LoggingManager;

public class DatabaseManager {

    private static final LoggingManager logger = LoggingManager.getLogger(DatabaseManager.class);
    private static DataMethods dataMethods;
    private final ConfigManager configManager;
    private final String databaseType;

    public DatabaseManager() {
        this.configManager = ConfigManager.getInstance();
        this.databaseType = configManager.getDatabaseType().toLowerCase();
    }

    public void initializeDatabase() {
        logger.database("Initializing database with type: %s", databaseType);

        dataMethods = new SQLiteStorage();
        logger.success("SQLite database initialized");
    }

    public static DataMethods getDataMethods() {
        return dataMethods;
    }
}
