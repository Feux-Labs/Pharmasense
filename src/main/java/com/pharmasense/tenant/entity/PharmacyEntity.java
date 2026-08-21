package com.pharmasense.tenant.entity;

import com.pharmasense.common.domain.AuditableEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A single pharmacy business (the tenant). Currency, timezone, and stock/
 * expiry alert thresholds are per-pharmacy rather than hardcoded, since
 * Pharmasense serves pharmacies across different countries and currencies.
 */
@Getter
@Setter
@Entity
@Table(name = "pharmacies")
public class PharmacyEntity extends AuditableEntity {

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "address_line")
    private String addressLine;

    private String city;

    private String country;

    @Column(name = "tax_registration_number")
    private String taxRegistrationNumber;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "USD";

    @Column(nullable = false)
    private String timezone = "UTC";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PharmacyPlanEnum plan = PharmacyPlanEnum.FREE_PILOT;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false, length = 32)
    private PharmacySubscriptionStatusEnum subscriptionStatus = PharmacySubscriptionStatusEnum.TRIALING;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "low_stock_threshold_default", nullable = false)
    private int lowStockThresholdDefault = 10;

    @Column(name = "expiry_warning_days_default", nullable = false)
    private int expiryWarningDaysDefault = 90;
}
