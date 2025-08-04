package net.kyver.invoices.model;

import net.kyver.invoices.enums.PaymentGateway;
import net.kyver.invoices.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Payment {

    private UUID paymentId;
    private UUID invoiceId;
    private String discordUserId;
    private PaymentGateway gateway;
    private String externalPaymentId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String paymentMethod;
    private String customerEmail;
    private String customerName;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
    private String failureReason;
    private Map<String, String> metadata;
    private String webhookEventId;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
    private String receiptUrl;
    private String refundId;

    public Payment() {
        this.paymentId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
        this.metadata = new HashMap<>();
    }

    public Payment(UUID invoiceId, String discordUserId, PaymentGateway gateway,
                   BigDecimal amount, String currency) {
        this();
        this.invoiceId = invoiceId;
        this.discordUserId = discordUserId;
        this.gateway = gateway;
        this.amount = amount;
        this.currency = currency;
        this.netAmount = amount;
    }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }

    public String getDiscordUserId() { return discordUserId; }
    public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }

    public PaymentGateway getGateway() { return gateway; }
    public void setGateway(PaymentGateway gateway) { this.gateway = gateway; }

    public String getExternalPaymentId() { return externalPaymentId; }
    public void setExternalPaymentId(String externalPaymentId) { this.externalPaymentId = externalPaymentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
        if (status == PaymentStatus.PAID && this.completedAt == null) {
            this.completedAt = LocalDateTime.now();
        }
    }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public void addMetadata(String key, String value) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, value);
    }

    public String getWebhookEventId() { return webhookEventId; }
    public void setWebhookEventId(String webhookEventId) { this.webhookEventId = webhookEventId; }

    public BigDecimal getFeeAmount() { return feeAmount; }
    public void setFeeAmount(BigDecimal feeAmount) {
        this.feeAmount = feeAmount;
        if (this.amount != null && feeAmount != null) {
            this.netAmount = this.amount.subtract(feeAmount);
        }
    }

    public BigDecimal getNetAmount() { return netAmount; }
    public void setNetAmount(BigDecimal netAmount) { this.netAmount = netAmount; }

    public String getReceiptUrl() { return receiptUrl; }
    public void setReceiptUrl(String receiptUrl) { this.receiptUrl = receiptUrl; }

    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }

    public boolean isCompleted() {
        return status == PaymentStatus.PAID;
    }

    public boolean isFailed() {
        return status == PaymentStatus.FAILED;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.PROCESSING;
    }

    public boolean isRefunded() {
        return status == PaymentStatus.REFUNDED;
    }

    public String getGatewayDisplayName() {
        return gateway != null ? gateway.getDisplayName() : "Unknown";
    }

    public String getFormattedAmount() {
        if (amount == null || currency == null) return "N/A";
        return String.format("%.2f %s", amount, currency.toUpperCase());
    }

    public String getStatusEmoji() {
        switch (status) {
            case PAID: return "✅";
            case FAILED: return "❌";
            case CANCELLED: return "🚫";
            case REFUNDED: return "💸";
            case PROCESSING: return "⏳";
            case OVERDUE: return "⚠️";
            default: return "🔄";
        }
    }
}
