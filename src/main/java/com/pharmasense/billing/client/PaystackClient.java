package com.pharmasense.billing.client;

import com.pharmasense.billing.config.PaystackProperties;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

/**
 * Thin wrapper around Paystack's Transactions API
 * (https://paystack.com/docs/api/transaction/). Kept separate from
 * {@code BillingService} so the payment-provider concern can be swapped
 * later without touching subscription/plan logic.
 */
@Component
public class PaystackClient {

    private static final Logger log = LoggerFactory.getLogger(PaystackClient.class);
    private static final String HMAC_ALGORITHM = "HmacSHA512";

    private final WebClient paystackWebClient;
    private final PaystackProperties properties;

    public PaystackClient(WebClient paystackWebClient, PaystackProperties properties) {
        this.paystackWebClient = paystackWebClient;
        this.properties = properties;
    }

    public record InitializeResult(String authorizationUrl, String accessCode, String reference) {
    }

    @SuppressWarnings("unchecked")
    public InitializeResult initializeTransaction(String email, long amountKobo, String reference, Map<String, Object> metadata) {
        Map<String, Object> requestBody = Map.of(
                "email", email,
                "amount", amountKobo,
                "reference", reference,
                "callback_url", properties.callbackUrl(),
                "metadata", metadata);

        try {
            Map<String, Object> response = paystackWebClient.post()
                    .uri("/transaction/initialize")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            Map<String, Object> data = (Map<String, Object>) response.get("data");
            return new InitializeResult(
                    (String) data.get("authorization_url"),
                    (String) data.get("access_code"),
                    (String) data.get("reference"));
        } catch (WebClientResponseException e) {
            log.error("Paystack rejected the checkout initialization: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "Could not start checkout right now. Please try again.", e);
        } catch (Exception e) {
            log.error("Paystack call failed", e);
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "Could not start checkout right now. Please try again.", e);
        }
    }

    /**
     * Paystack signs every webhook body with HMAC-SHA512 of the raw request
     * bytes, keyed by the secret key, hex-encoded in the
     * {@code x-paystack-signature} header - verifying this is the only thing
     * standing between the webhook endpoint and anyone on the internet
     * being able to grant themselves a free subscription.
     */
    public boolean verifySignature(String rawBody, String signatureHeader) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.secretKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] computed = mac.doFinal(rawBody.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return computedHex.equalsIgnoreCase(signatureHeader);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Could not compute Paystack webhook signature", e);
            return false;
        }
    }
}
