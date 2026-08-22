package com.pharmasense.billing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "pharmasense.billing")
public record BillingProperties(List<String> complimentaryEmails) {

    public BillingProperties {
        complimentaryEmails = complimentaryEmails == null ? List.of() : complimentaryEmails;
    }
}
