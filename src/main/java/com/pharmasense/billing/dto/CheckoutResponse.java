package com.pharmasense.billing.dto;

public record CheckoutResponse(String authorizationUrl, String reference) {
}
