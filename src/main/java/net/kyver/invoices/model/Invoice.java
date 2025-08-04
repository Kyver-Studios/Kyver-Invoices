package net.kyver.invoices.model;

import net.kyver.invoices.enums.PaymentGateway;
import net.kyver.invoices.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public class Invoice {
    private UUID invoiceId;
    private String discordUserId;
    private String channelId;
    private String customerEmail;
    private String customerName;
    private String description;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentGateway selectedGateway;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime dueDate;
    private String externalPaymentId;
    private String paymentUrl;
    private String qrCodeData;

    public Invoice() {
        this.invoiceId = UUID.randomUUID();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.status = PaymentStatus.PENDING;
    }

    public Invoice(String discordUserId, String channelId, String customerEmail, String customerName,
                   String description, BigDecimal amount, String currency) {
        this();
        this.discordUserId = discordUserId;
        this.channelId = channelId;
        this.customerEmail = customerEmail;
        this.customerName = customerName;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
    }

    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }

    public String getDiscordUserId() { return discordUserId; }
    public void setDiscordUserId(String discordUserId) { this.discordUserId = discordUserId; }

    public String getChannelId() { return channelId; }
    public void setChannelId(String channelId) { this.channelId = channelId; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public PaymentGateway getSelectedGateway() { return selectedGateway; }
    public void setSelectedGateway(PaymentGateway selectedGateway) { this.selectedGateway = selectedGateway; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDueDate() { return dueDate; }
    public void setDueDate(LocalDateTime dueDate) { this.dueDate = dueDate; }

    public String getExternalPaymentId() { return externalPaymentId; }
    public void setExternalPaymentId(String externalPaymentId) { this.externalPaymentId = externalPaymentId; }

    public String getPaymentUrl() { return paymentUrl; }
    public void setPaymentUrl(String paymentUrl) { this.paymentUrl = paymentUrl; }

    public String getQrCodeData() { return qrCodeData; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }

    public String getFormattedAmount() {
        return String.format("%.2f %s", amount, currency.toUpperCase());
    }

    public boolean isPaid() {
        return status == PaymentStatus.PAID;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }

    public boolean isCancelled() {
        return status == PaymentStatus.CANCELLED;
    }
}
