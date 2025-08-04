package net.kyver.invoices.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import net.kyver.invoices.manager.LoggingManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class QRCodeService {

    private static final LoggingManager logger = LoggingManager.getLogger(QRCodeService.class);
    private static final int QR_CODE_SIZE = 300;

    public static byte[] generateQRCode(String paymentUrl) throws IOException {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(paymentUrl, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            logger.info("Generated QR code for payment URL: " + paymentUrl.substring(0, Math.min(50, paymentUrl.length())) + "...");
            return outputStream.toByteArray();

        } catch (WriterException e) {
            logger.error("Failed to generate QR code for payment URL: " + paymentUrl, e);
            throw new IOException("Failed to generate QR code", e);
        }
    }

    public static boolean isValidUrl(String url) {
        return url != null &&
               !url.trim().isEmpty() &&
               (url.startsWith("http://") || url.startsWith("https://")) &&
               url.length() <= 2000;
    }
}
