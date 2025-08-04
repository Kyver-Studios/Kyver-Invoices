package net.kyver.invoices.manager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LoggingManager {

    public static final String RESET = "\u001B[0m";
    public static final String BLACK = "\u001B[30m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";

    public static final String BRIGHT_BLACK = "\u001B[90m";
    public static final String BRIGHT_RED = "\u001B[91m";
    public static final String BRIGHT_GREEN = "\u001B[92m";
    public static final String BRIGHT_YELLOW = "\u001B[93m";
    public static final String BRIGHT_BLUE = "\u001B[94m";
    public static final String BRIGHT_PURPLE = "\u001B[95m";
    public static final String BRIGHT_CYAN = "\u001B[96m";
    public static final String BRIGHT_WHITE = "\u001B[97m";

    public static final String BG_BLACK = "\u001B[40m";
    public static final String BG_RED = "\u001B[41m";
    public static final String BG_GREEN = "\u001B[42m";
    public static final String BG_YELLOW = "\u001B[43m";
    public static final String BG_BLUE = "\u001B[44m";
    public static final String BG_PURPLE = "\u001B[45m";
    public static final String BG_CYAN = "\u001B[46m";
    public static final String BG_WHITE = "\u001B[47m";

    public static final String BOLD = "\u001B[1m";
    public static final String UNDERLINE = "\u001B[4m";
    public static final String ITALIC = "\u001B[3m";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Logger logger;
    private final String prefix;
    private boolean colorEnabled = true;

    public LoggingManager(Class<?> clazz) {
        this.logger = LoggerFactory.getLogger(clazz);
        this.prefix = "[" + clazz.getSimpleName() + "]";
    }

    public LoggingManager(String name) {
        this.logger = LoggerFactory.getLogger(name);
        this.prefix = "[" + name + "]";
    }

    public static LoggingManager getLogger(Class<?> clazz) {
        return new LoggingManager(clazz);
    }

    public static LoggingManager getLogger(String name) {
        return new LoggingManager(name);
    }

    public void setColorEnabled(boolean colorEnabled) {
        this.colorEnabled = colorEnabled;
    }

    private String formatMessage(String level, String color, String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        if (colorEnabled) {
            return String.format("%s[%s]%s %s%s%s %s%s%s %s",
                    BRIGHT_BLACK, timestamp, RESET,
                    color, BOLD, level, RESET,
                    CYAN, prefix, RESET,
                    message);
        } else {
            return String.format("[%s] %s %s %s", timestamp, level, prefix, message);
        }
    }

    private void printToConsole(String formattedMessage) {
        System.out.println(formattedMessage);
    }

    public void info(String message) {
        String formatted = formatMessage("INFO", BRIGHT_GREEN, message);
        printToConsole(formatted);
        logger.info(message);
    }

    public void info(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        info(formattedMessage);
    }

    public void warn(String message) {
        String formatted = formatMessage("WARN", YELLOW, message);
        printToConsole(formatted);
        logger.warn(message);
    }

    public void warn(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        warn(formattedMessage);
    }

    public void warn(String message, Throwable throwable) {
        String formatted = formatMessage("WARN", YELLOW, message);
        printToConsole(formatted);
        if (throwable != null) {
            printToConsole(YELLOW + "Exception: " + throwable.getMessage() + RESET);
        }
        logger.warn(message, throwable);
    }

    public void error(String message) {
        String formatted = formatMessage("ERROR", BRIGHT_RED, message);
        printToConsole(formatted);
        logger.error(message);
    }

    public void error(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        error(formattedMessage);
    }

    public void error(String message, Throwable throwable) {
        String formatted = formatMessage("ERROR", BRIGHT_RED, message);
        printToConsole(formatted);
        if (throwable != null) {
            printToConsole(BRIGHT_RED + "Exception: " + throwable.getMessage() + RESET);
            if (logger.isDebugEnabled()) {
                throwable.printStackTrace();
            }
        }
        logger.error(message, throwable);
    }

    public void debug(String message) {
        String formatted = formatMessage("DEBUG", PURPLE, message);
        if (logger.isDebugEnabled()) {
            printToConsole(formatted);
        }
        logger.debug(message);
    }

    public void debug(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        debug(formattedMessage);
    }

    public void debug(String message, Throwable throwable) {
        String formatted = formatMessage("DEBUG", PURPLE, message);
        if (logger.isDebugEnabled()) {
            printToConsole(formatted);
            if (throwable != null) {
                printToConsole(PURPLE + "Exception: " + throwable.getMessage() + RESET);
            }
        }
        logger.debug(message, throwable);
    }

    public void success(String message) {
        String formatted = formatMessage("SUCCESS", BRIGHT_GREEN, "✓ " + message);
        printToConsole(formatted);
        logger.info("[SUCCESS] " + message);
    }

    public void success(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        success(formattedMessage);
    }

    public void payment(String message) {
        String formatted = formatMessage("PAYMENT", BRIGHT_CYAN, "💳 " + message);
        printToConsole(formatted);
        logger.info("[PAYMENT] " + message);
    }

    public void payment(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        payment(formattedMessage);
    }

    public void database(String message) {
        String formatted = formatMessage("DATABASE", BRIGHT_BLUE, "🗄️ " + message);
        printToConsole(formatted);
        logger.info("[DATABASE] " + message);
    }

    public void database(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        database(formattedMessage);
    }

    public void discord(String message) {
        String formatted = formatMessage("DISCORD", BRIGHT_PURPLE, "🤖 " + message);
        printToConsole(formatted);
        logger.info("[DISCORD] " + message);
    }

    public void discord(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        discord(formattedMessage);
    }

    public void startup(String message) {
        String formatted = formatMessage("STARTUP", BRIGHT_YELLOW, "🚀 " + message);
        printToConsole(formatted);
        logger.info("[STARTUP] " + message);
    }

    public void startup(String message, Object... args) {
        String formattedMessage = String.format(message, args);
        startup(formattedMessage);
    }

    public void colored(String color, String message) {
        if (colorEnabled) {
            printToConsole(color + message + RESET);
        } else {
            printToConsole(message);
        }
        logger.info(message);
    }

    public void printBanner(String title) {
        String border = "╔" + "═".repeat(title.length() + 4) + "╗";
        String content = "║  " + title + "  ║";
        String bottomBorder = "╚" + "═".repeat(title.length() + 4) + "╝";

        colored(BRIGHT_CYAN, border);
        colored(BRIGHT_CYAN, content);
        colored(BRIGHT_CYAN, bottomBorder);
    }

    public void separator() {
        colored(BRIGHT_BLACK, "─".repeat(50));
    }

    public void progress(String message, int current, int total) {
        int percentage = (int) ((double) current / total * 100);
        String progressBar = "█".repeat(percentage / 5) + "░".repeat(20 - percentage / 5);
        String formatted = String.format("%s [%s] %d%% (%d/%d)",
                message, progressBar, percentage, current, total);
        colored(BRIGHT_BLUE, formatted);
    }
}
