package net.kyver.invoices.data.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.kyver.invoices.data.DataMethods;
import net.kyver.invoices.model.Invoice;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.manager.LoggingManager;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

public class SQLiteStorage implements DataMethods {

    private static final LoggingManager logger = LoggingManager.getLogger(SQLiteStorage.class);
    private HikariDataSource hikariDataSource;

    public SQLiteStorage() {
        init();
    }

    public void init() {
        logger.database("Initializing SQLite database...");
        HikariConfig config = new HikariConfig();

        File dbFolder = new File("data");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }

        File dbFile = new File(dbFolder, "invoices.db");
        String dbPath = dbFile.getAbsolutePath();
        config.setJdbcUrl("jdbc:sqlite:" + dbPath);
        config.setConnectionTestQuery("SELECT 1");
        config.setMaximumPoolSize(1);
        config.setPoolName("SQLitePool");

        hikariDataSource = new HikariDataSource(config);

        try (Connection connection = hikariDataSource.getConnection();
             Statement statement = connection.createStatement()) {

            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");

            statement.execute("CREATE TABLE IF NOT EXISTS invoices (" +
                    "invoice_id TEXT PRIMARY KEY, " +
                    "discord_user_id TEXT NOT NULL, " +
                    "customer_email TEXT, " +
                    "customer_name TEXT, " +
                    "description TEXT, " +
                    "amount DECIMAL(10,2) NOT NULL, " +
                    "currency TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "created_at DATETIME NOT NULL, " +
                    "updated_at DATETIME NOT NULL, " +
                    "due_date DATETIME, " +
                    "payment_gateway TEXT, " +
                    "external_payment_id TEXT" +
                    ")");

            logger.success("SQLite database initialized successfully");

        } catch (SQLException e) {
            logger.error("Failed to initialize SQLite database", e);
        }
    }

    @Override
    public void createInvoice(Invoice invoice) {
        String sql = "INSERT INTO invoices (invoice_id, discord_user_id, customer_email, customer_name, " +
                "description, amount, currency, status, created_at, updated_at, due_date, " +
                "payment_gateway, external_payment_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, invoice.getInvoiceId().toString());
            statement.setString(2, invoice.getDiscordUserId());
            statement.setString(3, invoice.getCustomerEmail());
            statement.setString(4, invoice.getCustomerName());
            statement.setString(5, invoice.getDescription());
            statement.setBigDecimal(6, invoice.getAmount());
            statement.setString(7, invoice.getCurrency());
            statement.setString(8, invoice.getStatus().name());
            statement.setTimestamp(9, Timestamp.valueOf(invoice.getCreatedAt()));
            statement.setTimestamp(10, Timestamp.valueOf(invoice.getUpdatedAt()));
            statement.setTimestamp(11, invoice.getDueDate() != null ? Timestamp.valueOf(invoice.getDueDate()) : null);
            statement.setString(12, invoice.getSelectedGateway() != null ? invoice.getSelectedGateway().getId() : null);
            statement.setString(13, invoice.getExternalPaymentId());

            statement.executeUpdate();
            logger.database("Invoice created: %s", invoice.getInvoiceId());

        } catch (SQLException e) {
            logger.error("Failed to create invoice", e);
        }
    }

    @Override
    public Invoice getInvoice(UUID invoiceId) {
        String sql = "SELECT * FROM invoices WHERE invoice_id = ?";

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, invoiceId.toString());
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                logger.debug("Retrieved invoice: %s", invoiceId);
                return mapResultSetToInvoice(rs);
            }

        } catch (SQLException e) {
            logger.error("Failed to get invoice", e);
        }

        return null;
    }

    @Override
    public List<Invoice> getInvoicesByUser(String userId) {
        return getInvoicesByDiscordUser(userId);
    }

    @Override
    public List<Invoice> getInvoicesByDiscordUser(String discordUserId) {
        String sql = "SELECT * FROM invoices WHERE discord_user_id = ? ORDER BY created_at DESC";
        List<Invoice> invoices = new ArrayList<>();

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, discordUserId);
            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                invoices.add(mapResultSetToInvoice(rs));
            }

            logger.debug("Retrieved %d invoices for user: %s", invoices.size(), discordUserId);

        } catch (SQLException e) {
            logger.error("Failed to get invoices by discord user", e);
        }

        return invoices;
    }

    @Override
    public List<Invoice> getAllInvoices() {
        String sql = "SELECT * FROM invoices ORDER BY created_at DESC";
        List<Invoice> invoices = new ArrayList<>();

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            ResultSet rs = statement.executeQuery();

            while (rs.next()) {
                invoices.add(mapResultSetToInvoice(rs));
            }

            logger.debug("Retrieved %d total invoices", invoices.size());

        } catch (SQLException e) {
            logger.error("Failed to get all invoices", e);
        }

        return invoices;
    }

    @Override
    public void updateInvoiceStatus(UUID invoiceId, PaymentStatus status) {
        String sql = "UPDATE invoices SET status = ?, updated_at = ? WHERE invoice_id = ?";

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, status.name());
            statement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
            statement.setString(3, invoiceId.toString());

            statement.executeUpdate();
            logger.database("Invoice status updated: %s -> %s", invoiceId, status);

        } catch (SQLException e) {
            logger.error("Failed to update invoice status", e);
        }
    }

    @Override
    public void updateInvoice(Invoice invoice) {
        String sql = "UPDATE invoices SET discord_user_id = ?, customer_email = ?, customer_name = ?, " +
                "description = ?, amount = ?, currency = ?, status = ?, updated_at = ?, due_date = ?, " +
                "payment_gateway = ?, external_payment_id = ? WHERE invoice_id = ?";

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, invoice.getDiscordUserId());
            statement.setString(2, invoice.getCustomerEmail());
            statement.setString(3, invoice.getCustomerName());
            statement.setString(4, invoice.getDescription());
            statement.setBigDecimal(5, invoice.getAmount());
            statement.setString(6, invoice.getCurrency());
            statement.setString(7, invoice.getStatus().name());
            statement.setTimestamp(8, Timestamp.valueOf(invoice.getUpdatedAt()));
            statement.setTimestamp(9, invoice.getDueDate() != null ? Timestamp.valueOf(invoice.getDueDate()) : null);
            statement.setString(10, invoice.getSelectedGateway() != null ? invoice.getSelectedGateway().getId() : null);
            statement.setString(11, invoice.getExternalPaymentId());
            statement.setString(12, invoice.getInvoiceId().toString());

            int rowsAffected = statement.executeUpdate();
            if (rowsAffected > 0) {
                logger.database("Invoice updated: %s", invoice.getInvoiceId());
            } else {
                logger.warn("No invoice found to update: %s", invoice.getInvoiceId());
            }

        } catch (SQLException e) {
            logger.error("Failed to update invoice", e);
        }
    }

    @Override
    public void deleteInvoice(UUID invoiceId) {
        String sql = "DELETE FROM invoices WHERE invoice_id = ?";

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, invoiceId.toString());
            statement.executeUpdate();
            logger.database("Invoice deleted: %s", invoiceId);

        } catch (SQLException e) {
            logger.error("Failed to delete invoice", e);
        }
    }

    @Override
    public String getInvoiceIdByShortId(String shortId) {
        String sql = "SELECT invoice_id FROM invoices WHERE invoice_id LIKE ?";

        try (Connection connection = hikariDataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, shortId.toLowerCase() + "%");
            ResultSet rs = statement.executeQuery();

            if (rs.next()) {
                return rs.getString("invoice_id");
            }

        } catch (SQLException e) {
            logger.error("Failed to get invoice ID by short ID", e);
        }

        return null;
    }

    private Invoice mapResultSetToInvoice(ResultSet rs) throws SQLException {
        Invoice invoice = new Invoice();
        invoice.setInvoiceId(UUID.fromString(rs.getString("invoice_id")));
        invoice.setDiscordUserId(rs.getString("discord_user_id"));
        invoice.setCustomerEmail(rs.getString("customer_email"));
        invoice.setCustomerName(rs.getString("customer_name"));
        invoice.setDescription(rs.getString("description"));
        invoice.setAmount(rs.getBigDecimal("amount"));
        invoice.setCurrency(rs.getString("currency"));
        invoice.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        invoice.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        invoice.setUpdatedAt(rs.getTimestamp("updated_at").toLocalDateTime());

        Timestamp dueDate = rs.getTimestamp("due_date");
        if (dueDate != null) {
            invoice.setDueDate(dueDate.toLocalDateTime());
        }

        invoice.setSelectedGateway(net.kyver.invoices.enums.PaymentGateway.fromId(rs.getString("payment_gateway")));
        invoice.setExternalPaymentId(rs.getString("external_payment_id"));

        return invoice;
    }

    @Override
    public void close() {
        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            hikariDataSource.close();
            logger.database("SQLite connection closed");
        }
    }
}
