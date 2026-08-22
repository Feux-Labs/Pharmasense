package com.pharmasense.billing.service;

import com.pharmasense.billing.PlanLimits;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.repository.UserAccountRepository;
import com.pharmasense.inventory.repository.InventoryItemRepository;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import com.pharmasense.tenant.service.PharmacyService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * The one place that decides "is this pharmacy allowed to do X right now".
 * Called from the write paths it gates (inventory item creation, staff
 * invites, agent chat) rather than as a blanket filter, since each limit
 * needs a different count and a different error message.
 */
@Service
public class SubscriptionGuardService {

    private static final String AGENT_USAGE_KEY_PREFIX = "billing:agent-usage:";
    private static final DateTimeFormatter MONTH_KEY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final PharmacyService pharmacyService;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserAccountRepository userAccountRepository;
    private final StringRedisTemplate redisTemplate;

    public SubscriptionGuardService(
            PharmacyService pharmacyService,
            InventoryItemRepository inventoryItemRepository,
            UserAccountRepository userAccountRepository,
            StringRedisTemplate redisTemplate) {
        this.pharmacyService = pharmacyService;
        this.inventoryItemRepository = inventoryItemRepository;
        this.userAccountRepository = userAccountRepository;
        this.redisTemplate = redisTemplate;
    }

    public void assertCanCreateInventoryItem(UUID pharmacyId) {
        PharmacyEntity pharmacy = requireUsableSubscription(pharmacyId);
        if (pharmacy.isComplimentary()) return;

        long currentCount = inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId);
        int limit = PlanLimits.of(pharmacy.getPlan()).maxInventoryItems();
        if (currentCount >= limit) {
            throw new ApiException(ErrorCode.SUBSCRIPTION_LIMIT_REACHED,
                    "Your " + pharmacy.getPlan() + " plan allows up to " + limit + " inventory items. Upgrade to add more.");
        }
    }

    public void assertCanInviteStaff(UUID pharmacyId) {
        PharmacyEntity pharmacy = requireUsableSubscription(pharmacyId);
        if (pharmacy.isComplimentary()) return;

        long currentCount = userAccountRepository.countByPharmacyId(pharmacyId);
        int limit = PlanLimits.of(pharmacy.getPlan()).maxStaffUsers();
        if (currentCount >= limit) {
            throw new ApiException(ErrorCode.SUBSCRIPTION_LIMIT_REACHED,
                    "Your " + pharmacy.getPlan() + " plan allows up to " + limit + " staff accounts. Upgrade to add more.");
        }
    }

    /** Checks and atomically increments the pharmacy's monthly agent-message counter in one call. */
    public void assertCanUseAgentAndRecordUsage(UUID pharmacyId) {
        PharmacyEntity pharmacy = requireUsableSubscription(pharmacyId);
        if (pharmacy.isComplimentary()) return;

        int limit = PlanLimits.of(pharmacy.getPlan()).maxAgentMessagesPerMonth();
        String key = AGENT_USAGE_KEY_PREFIX + pharmacyId + ":" + MONTH_KEY_FORMAT.format(Instant.now());
        Long newCount = redisTemplate.opsForValue().increment(key);
        if (newCount != null && newCount == 1L) {
            // First message this month for this pharmacy - start the TTL so the
            // counter key doesn't live forever once a month rolls over.
            redisTemplate.expire(key, Duration.ofDays(35));
        }
        if (newCount != null && newCount > limit) {
            throw new ApiException(ErrorCode.SUBSCRIPTION_LIMIT_REACHED,
                    "Your " + pharmacy.getPlan() + " plan allows " + limit + " assistant messages per month. Upgrade for more.");
        }
    }

    /** Read-only peek at this month's agent-message count, for display - does not increment. */
    public long currentAgentMessageCount(UUID pharmacyId) {
        String key = AGENT_USAGE_KEY_PREFIX + pharmacyId + ":" + MONTH_KEY_FORMAT.format(Instant.now());
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0 : Long.parseLong(value);
    }

    /**
     * Confirms the pharmacy can use the product at all right now (trial not
     * expired, subscription not cancelled/suspended, paid period not
     * lapsed) and lazily downgrades to FREE the moment a paid period has
     * lapsed without renewal, rather than requiring a separate scheduled job.
     */
    @Transactional
    public PharmacyEntity requireUsableSubscription(UUID pharmacyId) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        if (pharmacy.isComplimentary()) {
            return pharmacy;
        }

        if (pharmacy.getSubscriptionStatus() == PharmacySubscriptionStatusEnum.CANCELLED
                || pharmacy.getSubscriptionStatus() == PharmacySubscriptionStatusEnum.SUSPENDED) {
            throw new ApiException(ErrorCode.TENANT_SUBSCRIPTION_INACTIVE,
                    "Your subscription is " + pharmacy.getSubscriptionStatus().name().toLowerCase()
                            + ". Reactivate your plan to continue.");
        }

        boolean trialExpired = pharmacy.getSubscriptionStatus() == PharmacySubscriptionStatusEnum.TRIALING
                && pharmacy.getTrialEndsAt() != null
                && pharmacy.getTrialEndsAt().isBefore(Instant.now());
        boolean paidPeriodExpired = pharmacy.getSubscriptionStatus() == PharmacySubscriptionStatusEnum.ACTIVE
                && pharmacy.getCurrentPeriodEndsAt() != null
                && pharmacy.getCurrentPeriodEndsAt().isBefore(Instant.now());

        if (trialExpired || paidPeriodExpired) {
            pharmacy.setPlan(PharmacyPlanEnum.FREE);
            pharmacy.setSubscriptionStatus(PharmacySubscriptionStatusEnum.ACTIVE);
            pharmacy.setCurrentPeriodEndsAt(null);
            pharmacyService.save(pharmacy);
        }

        return pharmacy;
    }
}
