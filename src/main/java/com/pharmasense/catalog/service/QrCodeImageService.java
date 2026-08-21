package com.pharmasense.catalog.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Renders a QR code PNG for any payload string - used for both the printable
 * shelf label (encodes the product-group scan URL) and the individual batch
 * sticker (encodes the batch scan URL). Kept generic on purpose: nothing
 * here knows about inventory, so it's reusable if we ever QR-code something
 * else (a supplier, a delivery box).
 */
@Service
public class QrCodeImageService {

    private static final int DEFAULT_SIZE_PX = 320;

    public byte[] generatePng(String payload, int sizePx) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1);

            BitMatrix matrix = new MultiFormatWriter().encode(
                    payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", outputStream);
            return outputStream.toByteArray();
        } catch (WriterException | IOException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Could not generate QR code", e);
        }
    }

    public byte[] generatePng(String payload) {
        return generatePng(payload, DEFAULT_SIZE_PX);
    }
}
