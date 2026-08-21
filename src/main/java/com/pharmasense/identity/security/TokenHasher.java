package com.pharmasense.identity.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates high-entropy opaque tokens (refresh tokens, catalog scan codes)
 * and hashes them for storage. We only ever persist the hash - the raw token
 * exists solely in the response sent to the client, so a database leak alone
 * can't be used to authenticate.
 */
@Component
public class TokenHasher {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public String generateOpaqueToken() {
        byte[] randomBytes = new byte[48];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String sha256(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
