package net.kyver.invoices.gateway.impl;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.PaymentLink;
import com.stripe.model.Refund;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PaymentLinkCreateParams;
import com.stripe.param.RefundCreateParams;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.exception.PaymentException;
import net.kyver.invoices.gateway.PaymentGateway;
import net.kyver.invoices.manager.LoggingManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class StripeGateway implements PaymentGateway {

    private static final LoggingManager logger = LoggingManager.getLogger(StripeGateway.class);
    private Consumer<String> paymentCompletedListener;

    public StripeGateway(String secretKey) {
        Stripe.apiKey = secretKey;
        logger.success("Stripe gateway initialized successfully");
    }

    @Override
    public CompletableFuture<String> processPaymentAsync(double amount, String currency, String paymentMethod, Map<String, String> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Processing Stripe payment: $%.2f %s", amount, currency.toUpperCase());

                long amountInCents = Math.round(amount * 100);

                PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                        .setAmount(amountInCents)
                        .setCurrency(currency.toLowerCase())
                        .setConfirmationMethod(PaymentIntentCreateParams.ConfirmationMethod.MANUAL)
                        .setConfirm(true);

                if (paymentMethod != null && !paymentMethod.isEmpty()) {
                    paramsBuilder.setPaymentMethod(paymentMethod);
                }

                if (metadata != null && !metadata.isEmpty()) {
                    paramsBuilder.putAllMetadata(metadata);
                }

                PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

                logger.success("Stripe payment intent created: %s", paymentIntent.getId());
                logger.payment("Payment status: %s", paymentIntent.getStatus());

                if ("succeeded".equals(paymentIntent.getStatus())) {
                    logger.success("Stripe payment completed successfully: %s", paymentIntent.getId());
                    if (paymentCompletedListener != null) {
                        paymentCompletedListener.accept(paymentIntent.getId());
                    }
                }

                return paymentIntent.getId();

            } catch (StripeException e) {
                logger.error("Stripe payment failed: %s", e.getMessage(), e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<String> refundPaymentAsync(String transactionId, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Processing Stripe refund for transaction: %s, amount: $%.2f", transactionId, amount);

                long amountInCents = Math.round(amount * 100);

                RefundCreateParams params = RefundCreateParams.builder()
                        .setPaymentIntent(transactionId)
                        .setAmount(amountInCents)
                        .build();

                Refund refund = Refund.create(params);

                logger.success("Stripe refund processed: %s", refund.getId());
                return refund.getId();

            } catch (StripeException e) {
                logger.error("Stripe refund failed: %s", e.getMessage(), e);
                return null;
            }
        });
    }

    @Override
    public boolean handleWebhook(Map<String, String> headers, String payload) throws PaymentException {
        try {
            logger.debug("Handling Stripe webhook");

            String sigHeader = headers.get("stripe-signature");
            if (sigHeader == null) {
                sigHeader = headers.get("Stripe-Signature");
            }

            if (sigHeader == null) {
                logger.warn("Missing Stripe signature header");
                return false;
            }

            if (payload.contains("\"type\":\"payment_intent.succeeded\"")) {
                logger.success("Stripe webhook: Payment succeeded");
                return true;
            }

            logger.debug("Stripe webhook processed successfully");
            return true;

        } catch (Exception e) {
            logger.error("Failed to handle Stripe webhook: %s", e.getMessage(), e);
            throw new PaymentException("Webhook handling failed", e);
        }
    }

    @Override
    public CompletableFuture<PaymentStatus> checkPaymentStatusAsync(String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Checking Stripe payment status for: %s", transactionId);

                PaymentIntent paymentIntent = PaymentIntent.retrieve(transactionId);
                String status = paymentIntent.getStatus();

                PaymentStatus paymentStatus = mapStripeStatus(status);
                logger.debug("Stripe payment status: %s -> %s", status, paymentStatus);

                return paymentStatus;

            } catch (StripeException e) {
                logger.error("Failed to check Stripe payment status: %s", e.getMessage(), e);
                return PaymentStatus.FAILED;
            }
        });
    }

    @Override
    public void onPaymentCompleted(Consumer<String> listener) {
        this.paymentCompletedListener = listener;
        logger.debug("Stripe payment completion listener registered");
    }

    private PaymentStatus mapStripeStatus(String stripeStatus) {
        switch (stripeStatus.toLowerCase()) {
            case "succeeded":
                return PaymentStatus.PAID;
            case "processing":
                return PaymentStatus.PROCESSING;
            case "requires_payment_method":
            case "requires_confirmation":
            case "requires_action":
                return PaymentStatus.PENDING;
            case "canceled":
                return PaymentStatus.CANCELLED;
            default:
                logger.warn("Unknown Stripe status: %s", stripeStatus);
                return PaymentStatus.PENDING;
        }
    }

    public CompletableFuture<String> createPaymentLink(double amount, String currency, Map<String, String> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Creating Stripe payment link: $%.2f %s", amount, currency.toUpperCase());

                PaymentLink paymentLink = com.stripe.model.PaymentLink.create(
                    PaymentLinkCreateParams.builder()
                        .addLineItem(
                            PaymentLinkCreateParams.LineItem.builder()
                                .setPrice(createPriceForPayment(amount, currency))
                                .setQuantity(1L)
                                .build()
                        )
                        .putAllMetadata(metadata != null ? metadata : Map.of())
                        .build()
                );

                logger.success("Stripe payment link created: %s", paymentLink.getId());
                return paymentLink.getUrl();

            } catch (StripeException e) {
                logger.error("Failed to create Stripe payment link: %s", e.getMessage(), e);
                return createFallbackPaymentUrl(amount, currency, metadata);
            }
        });
    }

    private String createPriceForPayment(double amount, String currency) throws StripeException {
        long amountInCents = Math.round(amount * 100);

        com.stripe.model.Price price = com.stripe.model.Price.create(
            com.stripe.param.PriceCreateParams.builder()
                .setCurrency(currency.toLowerCase())
                .setUnitAmount(amountInCents)
                .setProduct(getOrCreateProduct())
                .build()
        );

        return price.getId();
    }

    private String getOrCreateProduct() throws StripeException {
        try {
            com.stripe.model.Product product = com.stripe.model.Product.create(
                com.stripe.param.ProductCreateParams.builder()
                    .setName("Invoice Payment")
                    .setDescription("Payment for invoice via KyverInvoices")
                    .build()
            );
            return product.getId();
        } catch (StripeException e) {
            throw e;
        }
    }

    private String createFallbackPaymentUrl(double amount, String currency, Map<String, String> metadata) {
        try {
            long amountInCents = Math.round(amount * 100);

            PaymentIntentCreateParams.Builder paramsBuilder = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(currency.toLowerCase())
                .setAutomaticPaymentMethods(
                    PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build()
                );

            if (metadata != null && !metadata.isEmpty()) {
                paramsBuilder.putAllMetadata(metadata);
            }

            PaymentIntent paymentIntent = PaymentIntent.create(paramsBuilder.build());

            String webAppUrl = getWebAppUrl();
            return String.format("%s/payment/%s", webAppUrl, paymentIntent.getId());

        } catch (StripeException e) {
            logger.error("Fallback payment URL creation failed: %s", e.getMessage(), e);
            return "https://stripe.com";
        }
    }

    private String getWebAppUrl() {
        // Get the web app URL from config
        net.kyver.invoices.manager.ConfigManager configManager = net.kyver.invoices.manager.ConfigManager.getInstance();
        String webAppUrl = configManager.getWebApiUrl();

        if (webAppUrl == null || webAppUrl.isEmpty() || webAppUrl.equals("http://IP:PORT")) {
            logger.warn("Web API URL not configured, using default");
            return "https://checkout.stripe.com"; // Default fallback
        }

        return webAppUrl;
    }
}
