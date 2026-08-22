package com.pharmasense.tenant.service;

import com.pharmasense.billing.config.BillingProperties;
import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import com.pharmasense.tenant.repository.PharmacyRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class PharmacyService {

    private static final int TRIAL_LENGTH_DAYS = 90;

    private final PharmacyRepository pharmacyRepository;
    private final BillingProperties billingProperties;

    public PharmacyService(PharmacyRepository pharmacyRepository, BillingProperties billingProperties) {
        this.pharmacyRepository = pharmacyRepository;
        this.billingProperties = billingProperties;
    }

    @Transactional
    public PharmacyEntity registerPharmacy(String name, String contactEmail, String contactPhone, String currencyCode) {
        PharmacyEntity pharmacy = new PharmacyEntity();
        pharmacy.setName(name);
        pharmacy.setContactEmail(contactEmail.toLowerCase());
        pharmacy.setContactPhone(contactPhone);
        if (currencyCode != null && !currencyCode.isBlank()) {
            pharmacy.setCurrencyCode(currencyCode.toUpperCase());
        }

        if (billingProperties.complimentaryEmails().stream().anyMatch(email -> email.equalsIgnoreCase(contactEmail))) {
            pharmacy.setPlan(PharmacyPlanEnum.PRO);
            pharmacy.setSubscriptionStatus(PharmacySubscriptionStatusEnum.ACTIVE);
            pharmacy.setComplimentary(true);
        } else {
            pharmacy.setPlan(PharmacyPlanEnum.FREE);
            pharmacy.setSubscriptionStatus(PharmacySubscriptionStatusEnum.TRIALING);
            pharmacy.setTrialEndsAt(Instant.now().plus(TRIAL_LENGTH_DAYS, ChronoUnit.DAYS));
        }

        return pharmacyRepository.save(pharmacy);
    }

    public PharmacyEntity getById(UUID pharmacyId) {
        return pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Pharmacy", pharmacyId));
    }

    public Page<PharmacyEntity> listAll(Pageable pageable) {
        return pharmacyRepository.findAll(pageable);
    }

    @Transactional
    public PharmacyEntity save(PharmacyEntity pharmacy) {
        return pharmacyRepository.save(pharmacy);
    }
}
