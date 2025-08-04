package net.kyver.invoices.data;

import net.kyver.invoices.model.Invoice;
import net.kyver.invoices.enums.PaymentStatus;

import java.util.List;
import java.util.UUID;

public interface DataMethods {

    void createInvoice(Invoice invoice);
    Invoice getInvoice(UUID invoiceId);
    List<Invoice> getInvoicesByUser(String userId);
    List<Invoice> getAllInvoices();
    void updateInvoice(Invoice invoice);
    void updateInvoiceStatus(UUID invoiceId, PaymentStatus status);
    void deleteInvoice(UUID invoiceId);
    List<Invoice> getInvoicesByDiscordUser(String discordUserId);

    String getInvoiceIdByShortId(String shortId);

    void close();
}