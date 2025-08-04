package net.kyver.invoices.gateway.impl;

import com.paypal.api.payments.*;
import com.paypal.base.rest.APIContext;
import com.paypal.base.rest.PayPalRESTException;
import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.exception.PaymentException;
import net.kyver.invoices.gateway.PaymentGateway;
import net.kyver.invoices.manager.LoggingManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class PayPalGateway implements PaymentGateway {

    private static final LoggingManager logger = LoggingManager.getLogger(PayPalGateway.class);
    private final APIContext apiContext;
    private Consumer<String> paymentCompletedListener;

    public PayPalGateway(String clientId, String clientSecret, String mode) {
        this.apiContext = new APIContext(clientId, clientSecret, mode);
        logger.success("PayPal gateway initialized successfully in %s mode", mode);
    }

    @Override
    public CompletableFuture<String> processPaymentAsync(double amount, String currency, String paymentMethod, Map<String, String> metadata) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Processing PayPal payment: $%.2f %s", amount, currency.toUpperCase());

                Payment payment = createPayment(amount, currency, paymentMethod, "sale", metadata);
                Payment createdPayment = payment.create(apiContext);

                logger.success("PayPal payment created: %s", createdPayment.getId());

                String approvalUrl = null;
                for (Links link : createdPayment.getLinks()) {
                    if ("approval_url".equals(link.getRel())) {
                        approvalUrl = link.getHref();
                        break;
                    }
                }

                if (approvalUrl != null) {
                    logger.payment("PayPal approval URL: %s", approvalUrl);
                }

                return createdPayment.getId();

            } catch (PayPalRESTException e) {
                logger.error("PayPal payment failed: %s", e.getMessage(), e);
                return null;
            }
        });
    }

    @Override
    public CompletableFuture<String> refundPaymentAsync(String transactionId, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Processing PayPal refund for transaction: %s, amount: $%.2f", transactionId, amount);

                Payment payment = Payment.get(apiContext, transactionId);

                String saleId = null;
                for (Transaction transaction : payment.getTransactions()) {
                    for (RelatedResources resource : transaction.getRelatedResources()) {
                        if (resource.getSale() != null) {
                            saleId = resource.getSale().getId();
                            break;
                        }
                    }
                    if (saleId != null) break;
                }

                if (saleId == null) {
                    logger.error("No sale transaction found for payment: %s", transactionId);
                    return null;
                }

                RefundRequest refundRequest = new RefundRequest();
                Amount refundAmount = new Amount();
                refundAmount.setCurrency(payment.getTransactions().get(0).getAmount().getCurrency());
                refundAmount.setTotal(String.format("%.2f", amount));
                refundRequest.setAmount(refundAmount);

                Sale sale = new Sale();
                sale.setId(saleId);
                DetailedRefund refund = sale.refund(apiContext, refundRequest);

                logger.success("PayPal refund processed: %s", refund.getId());
                return refund.getId();

            } catch (PayPalRESTException e) {
                logger.error("PayPal refund failed: %s", e.getMessage(), e);
                return null;
            }
        });
    }

    @Override
    public boolean handleWebhook(Map<String, String> headers, String payload) throws PaymentException {
        try {
            logger.debug("Handling PayPal webhook");

            if (payload.contains("\"event_type\":\"PAYMENT.SALE.COMPLETED\"")) {
                logger.success("PayPal webhook: Payment completed");
                if (paymentCompletedListener != null) {
                }
                return true;
            }

            logger.debug("PayPal webhook processed successfully");
            return true;

        } catch (Exception e) {
            logger.error("Failed to handle PayPal webhook: %s", e.getMessage(), e);
            throw new PaymentException("PayPal webhook handling failed", e);
        }
    }

    @Override
    public CompletableFuture<PaymentStatus> checkPaymentStatusAsync(String transactionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.debug("Checking PayPal payment status for: %s", transactionId);

                Payment payment = Payment.get(apiContext, transactionId);
                String state = payment.getState();

                PaymentStatus paymentStatus = mapPayPalStatus(state);
                logger.debug("PayPal payment status: %s -> %s", state, paymentStatus);

                return paymentStatus;

            } catch (PayPalRESTException e) {
                logger.error("Failed to check PayPal payment status: %s", e.getMessage(), e);
                return PaymentStatus.FAILED;
            }
        });
    }

    @Override
    public void onPaymentCompleted(Consumer<String> listener) {
        this.paymentCompletedListener = listener;
        logger.debug("PayPal payment completion listener registered");
    }

    public CompletableFuture<Payment> executePayment(String paymentId, String payerId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.payment("Executing PayPal payment: %s with payer: %s", paymentId, payerId);

                Payment payment = new Payment();
                payment.setId(paymentId);

                PaymentExecution paymentExecution = new PaymentExecution();
                paymentExecution.setPayerId(payerId);

                Payment executedPayment = payment.execute(apiContext, paymentExecution);

                if ("approved".equals(executedPayment.getState())) {
                    logger.success("PayPal payment executed successfully: %s", paymentId);
                    if (paymentCompletedListener != null) {
                        paymentCompletedListener.accept(paymentId);
                    }
                }

                return executedPayment;

            } catch (PayPalRESTException e) {
                logger.error("PayPal payment execution failed: %s", e.getMessage(), e);
                return null;
            }
        });
    }

    private Payment createPayment(double amount, String currency, String paymentMethod, String intent, Map<String, String> metadata) {
        Amount amountObj = new Amount();
        amountObj.setCurrency(currency.toUpperCase());
        amountObj.setTotal(String.format("%.2f", amount));

        Transaction transaction = new Transaction();
        transaction.setDescription("Invoice Payment");
        transaction.setAmount(amountObj);

        if (metadata != null && !metadata.isEmpty()) {
            ItemList itemList = new ItemList();
            List<Item> items = new ArrayList<>();

            for (Map.Entry<String, String> entry : metadata.entrySet()) {
                Item item = new Item();
                item.setName(entry.getKey());
                item.setDescription(entry.getValue());
                item.setQuantity("1");
                item.setPrice("0.00");
                item.setCurrency(currency.toUpperCase());
                items.add(item);
            }

            itemList.setItems(items);
            transaction.setItemList(itemList);
        }

        Payer payer = new Payer();
        payer.setPaymentMethod(paymentMethod != null ? paymentMethod : "paypal");

        Payment payment = new Payment();
        payment.setIntent(intent);
        payment.setPayer(payer);
        payment.setTransactions(Arrays.asList(transaction));

        RedirectUrls redirectUrls = new RedirectUrls();
        redirectUrls.setCancelUrl("https://your-domain.com/cancel");
        redirectUrls.setReturnUrl("https://your-domain.com/success");
        payment.setRedirectUrls(redirectUrls);

        return payment;
    }

    private PaymentStatus mapPayPalStatus(String paypalState) {
        if (paypalState == null) return PaymentStatus.PENDING;

        switch (paypalState.toLowerCase()) {
            case "approved":
            case "completed":
                return PaymentStatus.PAID;
            case "created":
                return PaymentStatus.PENDING;
            case "cancelled":
            case "canceled":
                return PaymentStatus.CANCELLED;
            case "failed":
                return PaymentStatus.FAILED;
            default:
                logger.warn("Unknown PayPal status: %s", paypalState);
                return PaymentStatus.PENDING;
        }
    }

    public String getApprovalUrl(String paymentId) {
        try {
            Payment payment = Payment.get(apiContext, paymentId);
            for (Links link : payment.getLinks()) {
                if ("approval_url".equals(link.getRel())) {
                    return link.getHref();
                }
            }
        } catch (PayPalRESTException e) {
            logger.error("Failed to get PayPal approval URL: %s", e.getMessage(), e);
        }
        return null;
    }
}
