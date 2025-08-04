package net.kyver.invoices.manager;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import net.kyver.invoices.api.WebhookHandler;
import net.kyver.invoices.gateway.impl.PayPalGateway;
import net.kyver.invoices.gateway.impl.StripeGateway;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.concurrent.Executors;

public class WebApiManager {

    private static final LoggingManager logger = LoggingManager.getLogger(WebApiManager.class);
    private final ConfigManager configManager;
    private HttpServer server;
    private boolean isHttps;
    private PayPalGateway paypalGateway;
    private StripeGateway stripeGateway;

    public WebApiManager(ConfigManager configManager) {
        this.configManager = configManager;
        this.isHttps = configManager.getWebApiUrl().startsWith("https://");
    }

    public void startServer() {
        try {
            int port = configManager.getWebApiPort();
            logger.startup("Starting web API server on port %d...", port);

            if (isHttps) {
                startHttpsServer(port);
            } else {
                startHttpServer(port);
            }

            setupRoutes();
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();

            logger.success("Web API server started successfully!");
            logger.info("Server URL: %s", configManager.getWebApiUrl());
            logger.info("HTTPS Enabled: %s", isHttps ? "✅" : "❌");

        } catch (Exception e) {
            logger.error("Failed to start web API server", e);
            throw new RuntimeException("Web API startup failed", e);
        }
    }

    private void startHttpServer(int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        logger.info("HTTP server created on port %d", port);
    }

    private void startHttpsServer(int port) throws Exception {
        server = HttpsServer.create(new InetSocketAddress(port), 0);

        SSLContext sslContext = createSSLContext();
        ((HttpsServer) server).setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    SSLContext context = getSSLContext();
                    params.setSSLParameters(context.getDefaultSSLParameters());
                } catch (Exception e) {
                    logger.error("Failed to configure HTTPS parameters", e);
                }
            }
        });

        logger.success("HTTPS server created on port %d", port);
    }

    private SSLContext createSSLContext() throws Exception {
        String keystorePath = configManager.getString("web_api.ssl.keystore_path", "keystore.jks");
        String keystorePassword = configManager.getString("web_api.ssl.keystore_password", "changeit");
        String keyPassword = configManager.getString("web_api.ssl.key_password", "changeit");

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(keystorePath)) {
            keyStore.load(fis, keystorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        keyManagerFactory.init(keyStore, keyPassword.toCharArray());

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("SunX509");
        trustManagerFactory.init(keyStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(
            keyManagerFactory.getKeyManagers(),
            trustManagerFactory.getTrustManagers(),
            null
        );

        return sslContext;
    }

    private void setupRoutes() {
        initializePaymentGateways();

        WebhookHandler webhookHandler = new WebhookHandler(configManager, paypalGateway, stripeGateway);

        server.createContext("/api/webhook/paypal", webhookHandler::handlePayPalWebhook);
        server.createContext("/api/webhook/stripe", webhookHandler::handleStripeWebhook);

        server.createContext("/api/health", exchange -> {
            String response = "{\"status\":\"ok\",\"timestamp\":" + System.currentTimeMillis() + "}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        server.createContext("/", exchange -> {
            String response = "{\"service\":\"KyverInvoices API\",\"version\":\"1.0\"}";
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length());
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();
        });

        logger.info("API routes configured:");
        logger.info("  GET  /api/health - Health check");
        logger.info("  POST /api/webhook/paypal - PayPal webhooks");
        logger.info("  POST /api/webhook/stripe - Stripe webhooks");
    }

    private void initializePaymentGateways() {
        if (configManager.isPayPalEnabled()) {
            try {
                paypalGateway = new PayPalGateway(
                    configManager.getPayPalClientId(),
                    configManager.getPayPalClientSecret(),
                    configManager.getPayPalMode()
                );
                logger.success("PayPal gateway initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize PayPal gateway", e);
            }
        }

        if (configManager.isStripeEnabled()) {
            try {
                stripeGateway = new StripeGateway(configManager.getStripeSecretKey());
                logger.success("Stripe gateway initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize Stripe gateway", e);
            }
        }
    }

    public void stopServer() {
        if (server != null) {
            logger.info("Stopping web API server...");
            server.stop(0);
            logger.success("Web API server stopped");
        }
    }

    public boolean isRunning() {
        return server != null;
    }

    public PayPalGateway getPaypalGateway() {
        return paypalGateway;
    }

    public StripeGateway getStripeGateway() {
        return stripeGateway;
    }
}
