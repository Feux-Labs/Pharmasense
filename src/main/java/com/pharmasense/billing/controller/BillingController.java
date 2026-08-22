package com.pharmasense.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasense.billing.client.PaystackClient;
import com.pharmasense.billing.dto.CheckoutRequest;
import com.pharmasense.billing.dto.CheckoutResponse;
import com.pharmasense.billing.dto.SubscriptionResponse;
import com.pharmasense.billing.service.BillingService;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Billing")
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingService billingService;
    private final PaystackClient paystackClient;
    private final ObjectMapper objectMapper;

    public BillingController(BillingService billingService, PaystackClient paystackClient, ObjectMapper objectMapper) {
        this.billingService = billingService;
        this.paystackClient = paystackClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/subscription")
    public ApiResponse<SubscriptionResponse> getSubscription(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        return ApiResponse.ok(billingService.getSubscription(principal.pharmacyId()));
    }

    @PostMapping("/checkout")
    public ApiResponse<CheckoutResponse> checkout(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal, @Valid @RequestBody CheckoutRequest request) {
        return ApiResponse.ok(billingService.startCheckout(principal.pharmacyId(), request.plan()));
    }

    @PostMapping("/cancel")
    public ApiResponse<Void> cancel(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        billingService.cancelSubscription(principal.pharmacyId());
        return ApiResponse.ok(null, "Downgraded to the free plan");
    }

    /**
     * Public (see SecurityConfig) - Paystack calls this directly, so the
     * only thing standing between this endpoint and anyone on the internet
     * granting themselves a subscription is the signature check below.
     * Takes the raw body as a String rather than a parsed DTO because the
     * signature is computed over the exact bytes Paystack sent, not over
     * whatever Jackson would re-serialize them as.
     */
    @PostMapping("/webhook")
    public void webhook(
            @RequestBody String rawBody, @RequestHeader(value = "x-paystack-signature", required = false) String signature) {
        if (!paystackClient.verifySignature(rawBody, signature)) {
            log.warn("Rejected Paystack webhook with invalid signature");
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Invalid webhook signature");
        }
        try {
            Map<String, Object> event = objectMapper.readValue(rawBody, Map.class);
            billingService.handleWebhookEvent(event);
        } catch (Exception e) {
            log.error("Failed to process Paystack webhook payload", e);
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Malformed webhook payload");
        }
    }
}
