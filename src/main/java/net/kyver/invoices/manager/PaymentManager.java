package net.kyver.invoices.manager;

import net.kyver.invoices.data.DatabaseManager;
import net.kyver.invoices.enums.PaymentGateway;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.exception.PaymentException;
import net.kyver.invoices.gateway.impl.PayPalGateway;
import net.kyver.invoices.gateway.impl.StripeGateway;
import net.kyver.invoices.model.Invoice;
import net.kyver.invoices.model.Payment;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PaymentManager {

    private static final LoggingManager logger = LoggingManager.getLogger(PaymentManager.class);
    private static PaymentManager instance;
    private final ConfigManager configManager;
    private final Map<PaymentGateway, net.kyver.invoices.gateway.PaymentGateway> gateways;
    private final Map<UUID, Payment> activePayments;

    private PaymentManager() {
        this.configManager = ConfigManager.getInstance();
        this.gateways = new ConcurrentHashMap<>();
        this.activePayments = new ConcurrentHashMap<>();
        initializeGateways();
    }

    public static PaymentManager getInstance() {
        if (instance == null) {
            instance = new PaymentManager();
        }
        return instance;
    }

    private void initializeGateways() {
        logger.payment("Initializing payment gateways...");

        if (configManager.isStripeEnabled()) {
            try {
                StripeGateway stripeGateway = new StripeGateway(configManager.getStripeSecretKey());
                gateways.put(PaymentGateway.STRIPE, stripeGateway);
                logger.success("Stripe gateway initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize Stripe gateway", e);
            }
        }

        if (configManager.isPayPalEnabled()) {
            try {
                PayPalGateway paypalGateway = new PayPalGateway(
                    configManager.getPayPalClientId(),
                    configManager.getPayPalClientSecret(),
                    configManager.getPayPalMode()
                );
                gateways.put(PaymentGateway.PAYPAL, paypalGateway);
                logger.success("PayPal gateway initialized");
            } catch (Exception e) {
                logger.error("Failed to initialize PayPal gateway", e);
            }
        }

        logger.payment("Payment gateways initialized: %d active", gateways.size());
    }

    public CompletableFuture<Payment> createPayment(UUID invoiceId, String discordUserId,
                                                   PaymentGateway gateway, String customerEmail,
                                                   String customerName) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Creating payment for invoice %s with gateway %s", invoiceId, gateway.getDisplayName());

                Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
                if (invoice == null) {
                    throw new PaymentException("Invoice not found: " + invoiceId);
                }

                if (!gateways.containsKey(gateway)) {
                    throw new PaymentException("Payment gateway not available: " + gateway.getDisplayName());
                }

                Payment payment = new Payment(invoiceId, discordUserId, gateway,
                                            invoice.getAmount(), invoice.getCurrency());
                payment.setCustomerEmail(customerEmail);
                payment.setCustomerName(customerName);
                payment.setDescription(invoice.getDescription());
                payment.addMetadata("invoice_id", invoiceId.toString());
                payment.addMetadata("discord_user_id", discordUserId);

                activePayments.put(payment.getPaymentId(), payment);

                return processPaymentWithGateway(payment, gateway);

            } catch (Exception e) {
                logger.error("Failed to create payment", e);
                throw new PaymentException("Payment creation failed: " + e.getMessage(), e);
            }
        });
    }

    public String generatePaymentLink(Invoice invoice, PaymentGateway gateway) {
        try {
            logger.payment("Generating payment link for invoice %s with gateway %s", invoice.getInvoiceId(), gateway);

            net.kyver.invoices.gateway.PaymentGateway gatewayImpl = gateways.get(gateway);
            if (gatewayImpl == null) {
                throw new PaymentException("Gateway not available: " + gateway.getDisplayName());
            }

            Map<String, String> metadata = new HashMap<>();
            metadata.put("invoice_id", invoice.getInvoiceId().toString());
            metadata.put("discord_user_id", invoice.getDiscordUserId());

            switch (gateway) {
                case STRIPE:
                    StripeGateway stripeGateway = (StripeGateway) gatewayImpl;
                    return stripeGateway.createPaymentLink(
                        invoice.getAmount().doubleValue(),
                        invoice.getCurrency(),
                        metadata
                    ).join();

                case PAYPAL:
                    String paymentId = gatewayImpl.processPaymentAsync(
                        invoice.getAmount().doubleValue(),
                        invoice.getCurrency(),
                        "paypal",
                        metadata
                    ).join();

                    PayPalGateway paypalGateway = (PayPalGateway) gatewayImpl;
                    return paypalGateway.getApprovalUrl(paymentId);

                default:
                    throw new PaymentException("Payment links not supported for: " + gateway.getDisplayName());
            }

        } catch (Exception e) {
            logger.error("Failed to generate payment link", e);
            return null;
        }
    }

    public byte[] generateQRCode(String paymentLink) {
        try {
            String qrUrl = String.format("https://api.qrserver.com/v1/create-qr-code/?size=300x300&data=%s",
                java.net.URLEncoder.encode(paymentLink, "UTF-8"));

            java.net.URL url = new java.net.URL(qrUrl);
            java.io.InputStream inputStream = url.openStream();
            byte[] qrCodeBytes = inputStream.readAllBytes();
            inputStream.close();

            logger.debug("Generated QR code for payment link");
            return qrCodeBytes;

        } catch (Exception e) {
            logger.error("Failed to generate QR code", e);
            return null;
        }
    }

    public CompletableFuture<String> createPaymentLink(UUID invoiceId, PaymentGateway gateway) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Creating payment link for invoice %s", invoiceId);

                Invoice invoice = DatabaseManager.getDataMethods().getInvoice(invoiceId);
                if (invoice == null) {
                    throw new PaymentException("Invoice not found: " + invoiceId);
                }

                net.kyver.invoices.gateway.PaymentGateway gatewayImpl = gateways.get(gateway);
                if (gatewayImpl == null) {
                    throw new PaymentException("Gateway not available: " + gateway.getDisplayName());
                }

                Map<String, String> metadata = new HashMap<>();
                metadata.put("invoice_id", invoiceId.toString());
                metadata.put("discord_user_id", invoice.getDiscordUserId());

                switch (gateway) {
                    case STRIPE:
                        StripeGateway stripeGateway = (StripeGateway) gatewayImpl;
                        return stripeGateway.createPaymentLink(
                            invoice.getAmount().doubleValue(),
                            invoice.getCurrency(),
                            metadata
                        ).join();

                    case PAYPAL:
                        String paymentId = gatewayImpl.processPaymentAsync(
                            invoice.getAmount().doubleValue(),
                            invoice.getCurrency(),
                            "paypal",
                            metadata
                        ).join();

                        PayPalGateway paypalGateway = (PayPalGateway) gatewayImpl;
                        return paypalGateway.getApprovalUrl(paymentId);

                    default:
                        throw new PaymentException("Payment links not supported for: " + gateway.getDisplayName());
                }

            } catch (Exception e) {
                logger.error("Failed to create payment link", e);
                throw new PaymentException("Payment link creation failed: " + e.getMessage(), e);
            }
        });
    }

    private Payment processPaymentWithGateway(Payment payment, PaymentGateway gateway) {
        try {
            net.kyver.invoices.gateway.PaymentGateway gatewayImpl = gateways.get(gateway);

            Map<String, String> metadata = payment.getMetadata();

            String externalPaymentId = gatewayImpl.processPaymentAsync(
                payment.getAmount().doubleValue(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                metadata
            ).join();

            payment.setExternalPaymentId(externalPaymentId);
            payment.setStatus(PaymentStatus.PROCESSING);

            logger.success("Payment processed with %s: %s", gateway.getDisplayName(), externalPaymentId);
            return payment;

        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(e.getMessage());
            logger.error("Payment processing failed", e);
            throw new PaymentException("Payment processing failed: " + e.getMessage(), e);
        }
    }

    public CompletableFuture<String> refundPayment(UUID paymentId, BigDecimal amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Payment payment = activePayments.get(paymentId);
                if (payment == null) {
                    throw new PaymentException("Payment not found: " + paymentId);
                }

                if (!payment.isCompleted()) {
                    throw new PaymentException("Cannot refund non-completed payment");
                }

                net.kyver.invoices.gateway.PaymentGateway gateway = gateways.get(payment.getGateway());
                if (gateway == null) {
                    throw new PaymentException("Gateway not available for refund");
                }

                logger.payment("Processing refund for payment %s", paymentId);

                String refundId = gateway.refundPaymentAsync(
                    payment.getExternalPaymentId(),
                    amount.doubleValue()
                ).join();

                payment.setRefundId(refundId);
                payment.setStatus(PaymentStatus.REFUNDED);

                logger.success("Payment refunded: %s", refundId);
                return refundId;

            } catch (Exception e) {
                logger.error("Refund failed", e);
                throw new PaymentException("Refund failed: " + e.getMessage(), e);
            }
        });
    }

    public CompletableFuture<PaymentStatus> checkPaymentStatus(UUID paymentId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Payment payment = activePayments.get(paymentId);
                if (payment == null) {
                    throw new PaymentException("Payment not found: " + paymentId);
                }

                net.kyver.invoices.gateway.PaymentGateway gateway = gateways.get(payment.getGateway());
                if (gateway == null) {
                    return payment.getStatus();
                }

                PaymentStatus status = gateway.checkPaymentStatusAsync(payment.getExternalPaymentId()).join();
                payment.setStatus(status);

                return status;

            } catch (Exception e) {
                logger.error("Failed to check payment status", e);
                return PaymentStatus.FAILED;
            }
        });
    }

    public List<PaymentGateway> getAvailableGateways() {
        return new ArrayList<>(gateways.keySet());
    }

    public boolean isGatewayAvailable(PaymentGateway gateway) {
        return gateways.containsKey(gateway);
    }

    public boolean hasAvailableGateways() {
        return !gateways.isEmpty();
    }

    public Payment getPayment(UUID paymentId) {
        return activePayments.get(paymentId);
    }

    public List<Payment> getPaymentsByUser(String discordUserId) {
        return activePayments.values().stream()
                .filter(payment -> discordUserId.equals(payment.getDiscordUserId()))
                .sorted((p1, p2) -> p2.getCreatedAt().compareTo(p1.getCreatedAt()))
                .toList();
    }

    public net.kyver.invoices.gateway.PaymentGateway getGateway(PaymentGateway gateway) {
        return gateways.get(gateway);
    }

    public void handleWebhookEvent(PaymentGateway gateway, String paymentId, PaymentStatus status) {
        try {
            Payment payment = activePayments.values().stream()
                    .filter(p -> paymentId.equals(p.getExternalPaymentId()))
                    .findFirst()
                    .orElse(null);

            if (payment != null) {
                payment.setStatus(status);
                logger.payment("Payment status updated via webhook: %s -> %s", paymentId, status);

                if (status == PaymentStatus.PAID) {
                    DatabaseManager.getDataMethods().updateInvoiceStatus(payment.getInvoiceId(), PaymentStatus.PAID);
                    logger.success("Invoice marked as paid: %s", payment.getInvoiceId());
                }
            }

        } catch (Exception e) {
            logger.error("Failed to handle webhook event", e);
        }
    }

    public Map<String, Object> getPaymentStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalPayments = activePayments.size();
        long completedPayments = activePayments.values().stream().mapToLong(p -> p.isCompleted() ? 1 : 0).sum();
        long pendingPayments = activePayments.values().stream().mapToLong(p -> p.isPending() ? 1 : 0).sum();
        long failedPayments = activePayments.values().stream().mapToLong(p -> p.isFailed() ? 1 : 0).sum();

        BigDecimal totalAmount = activePayments.values().stream()
                .filter(Payment::isCompleted)
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.put("total_payments", totalPayments);
        stats.put("completed_payments", completedPayments);
        stats.put("pending_payments", pendingPayments);
        stats.put("failed_payments", failedPayments);
        stats.put("total_amount", totalAmount);
        stats.put("available_gateways", gateways.size());

        return stats;
    }
}
