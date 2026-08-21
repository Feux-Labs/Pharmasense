package com.pharmasense.admin.service;

import com.pharmasense.admin.dto.PlatformAnalyticsResponse;
import com.pharmasense.identity.repository.UserAccountRepository;
import com.pharmasense.inventory.repository.InventoryItemRepository;
import com.pharmasense.prescription.repository.PrescriptionRepository;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import com.pharmasense.tenant.repository.PharmacyRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Cross-tenant counts for the super-admin dashboard - the only place in the codebase allowed to query across every pharmacy at once. */
@Service
public class AdminAnalyticsService {

    private final PharmacyRepository pharmacyRepository;
    private final UserAccountRepository userAccountRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final PrescriptionRepository prescriptionRepository;

    public AdminAnalyticsService(
            PharmacyRepository pharmacyRepository,
            UserAccountRepository userAccountRepository,
            InventoryItemRepository inventoryItemRepository,
            PrescriptionRepository prescriptionRepository) {
        this.pharmacyRepository = pharmacyRepository;
        this.userAccountRepository = userAccountRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.prescriptionRepository = prescriptionRepository;
    }

    public PlatformAnalyticsResponse getOverview() {
        return new PlatformAnalyticsResponse(
                pharmacyRepository.count(),
                pharmacyRepository.countBySubscriptionStatus(PharmacySubscriptionStatusEnum.TRIALING),
                pharmacyRepository.countBySubscriptionStatus(PharmacySubscriptionStatusEnum.ACTIVE),
                userAccountRepository.count(),
                inventoryItemRepository.count(),
                prescriptionRepository.count(),
                Instant.now());
    }
}
