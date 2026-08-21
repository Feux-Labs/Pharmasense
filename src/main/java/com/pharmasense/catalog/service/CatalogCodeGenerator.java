package com.pharmasense.catalog.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Short, scan-friendly codes rather than full UUIDs - a UUID QR code needs
 * more modules (a denser, harder-to-scan grid at small sticker sizes) and is
 * not something a staff member could ever read and re-type if a scan fails.
 * Prefixed by type so {@code CatalogScanService} can tell an item code from
 * a batch code without a database round-trip.
 */
@Component
public class CatalogCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"; // no 0/O/1/I - easier to read off a label
    private static final int RANDOM_PART_LENGTH = 8;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public static final String ITEM_PREFIX = "ITM";
    public static final String BATCH_PREFIX = "BAT";

    public String generateItemCode() {
        return generate(ITEM_PREFIX);
    }

    public String generateBatchCode() {
        return generate(BATCH_PREFIX);
    }

    private String generate(String prefix) {
        StringBuilder builder = new StringBuilder(prefix).append('-');
        for (int i = 0; i < RANDOM_PART_LENGTH; i++) {
            builder.append(ALPHABET.charAt(SECURE_RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }
}
