package net.kyver.invoices.gateway;

import net.kyver.invoices.enums.PaymentStatus;
import net.kyver.invoices.exception.PaymentException;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PaymentGateway {

    CompletableFuture<String> processPaymentAsync(double amount, String currency, String paymentMethod, Map<String, String> metadata);

    CompletableFuture<String> refundPaymentAsync(String transactionId, double amount);

    boolean handleWebhook(Map<String, String> headers, String payload) throws PaymentException;

    CompletableFuture<PaymentStatus> checkPaymentStatusAsync(String transactionId);

    void onPaymentCompleted(Consumer<String> listener);
}
