package com.pharmasense.billing.service;

import com.pharmasense.billing.config.BillingProperties;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import com.pharmasense.tenant.repository.PharmacyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * {@code PharmacyService#registerPharmacy} already grants a complimentary
 * PRO subscription to a brand-new signup matching
 * {@code pharmasense.billing.complimentary-emails} - this covers the other
 * case, where that pharmacy already existed before an email was added to
 * the list (or before this feature existed at all).
 */
@Component
public class ComplimentaryAccountBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ComplimentaryAccountBootstrapRunner.class);

    private final PharmacyRepository pharmacyRepository;
    private final BillingProperties billingProperties;

    public ComplimentaryAccountBootstrapRunner(PharmacyRepository pharmacyRepository, BillingProperties billingProperties) {
        this.pharmacyRepository = pharmacyRepository;
        this.billingProperties = billingProperties;
    }

    @Override
    public void run(String... args) {
        for (String email : billingProperties.complimentaryEmails()) {
            pharmacyRepository.findByContactEmailIgnoreCase(email).ifPresent(this::grantComplimentaryAccess);
        }
    }

    private void grantComplimentaryAccess(PharmacyEntity pharmacy) {
        if (pharmacy.isComplimentary()) {
            return;
        }
        pharmacy.setPlan(PharmacyPlanEnum.PRO);
        pharmacy.setSubscriptionStatus(PharmacySubscriptionStatusEnum.ACTIVE);
        pharmacy.setComplimentary(true);
        pharmacyRepository.save(pharmacy);
        log.info("Granted complimentary PRO access to existing pharmacy {} ({})", pharmacy.getId(), pharmacy.getContactEmail());
    }
}
