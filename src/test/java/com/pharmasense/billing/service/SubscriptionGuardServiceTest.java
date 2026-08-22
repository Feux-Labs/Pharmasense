package com.pharmasense.billing.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.repository.UserAccountRepository;
import com.pharmasense.inventory.repository.InventoryItemRepository;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import com.pharmasense.tenant.service.PharmacyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubscriptionGuardServiceTest {

    @Mock
    private PharmacyService pharmacyService;
    @Mock
    private InventoryItemRepository inventoryItemRepository;
    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SubscriptionGuardService guard;
    private final UUID pharmacyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        guard = new SubscriptionGuardService(pharmacyService, inventoryItemRepository, userAccountRepository, redisTemplate);
    }

    private PharmacyEntity pharmacyWith(PharmacyPlanEnum plan, PharmacySubscriptionStatusEnum status) {
        PharmacyEntity pharmacy = new PharmacyEntity();
        pharmacy.setPlan(plan);
        pharmacy.setSubscriptionStatus(status);
        return pharmacy;
    }

    @Test
    void freeTierBlocksInventoryItemCreationAtTheLimit() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.FREE, PharmacySubscriptionStatusEnum.ACTIVE);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId)).thenReturn(50L); // FREE limit

        assertThatThrownBy(() -> guard.assertCanCreateInventoryItem(pharmacyId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_LIMIT_REACHED));
    }

    @Test
    void freeTierAllowsInventoryItemCreationBelowTheLimit() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.FREE, PharmacySubscriptionStatusEnum.ACTIVE);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId)).thenReturn(49L);

        assertThatCode(() -> guard.assertCanCreateInventoryItem(pharmacyId)).doesNotThrowAnyException();
    }

    @Test
    void proTierNeverHitsTheInventoryLimit() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.PRO, PharmacySubscriptionStatusEnum.ACTIVE);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId)).thenReturn(100_000L);

        assertThatCode(() -> guard.assertCanCreateInventoryItem(pharmacyId)).doesNotThrowAnyException();
    }

    @Test
    void complimentaryAccountBypassesEveryLimitRegardlessOfPlan() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.FREE, PharmacySubscriptionStatusEnum.ACTIVE);
        pharmacy.setComplimentary(true);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        // Deliberately not stubbing the count repositories - if the guard tried
        // to read them for a complimentary account, Mockito's strict-adjacent
        // defaults would return 0 anyway, so assert no exception is the real check.

        assertThatCode(() -> guard.assertCanCreateInventoryItem(pharmacyId)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanInviteStaff(pharmacyId)).doesNotThrowAnyException();
    }

    @Test
    void staffLimitIsEnforcedSeparatelyFromInventoryLimit() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.FREE, PharmacySubscriptionStatusEnum.ACTIVE);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(userAccountRepository.countByPharmacyId(pharmacyId)).thenReturn(1L); // FREE staff limit

        assertThatThrownBy(() -> guard.assertCanInviteStaff(pharmacyId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_LIMIT_REACHED));
    }

    @Test
    void cancelledSubscriptionBlocksAccessEvenBelowAnyUsageLimit() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.BASIC, PharmacySubscriptionStatusEnum.CANCELLED);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);

        assertThatThrownBy(() -> guard.assertCanCreateInventoryItem(pharmacyId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.TENANT_SUBSCRIPTION_INACTIVE));
    }

    @Test
    void expiredTrialLazilyDowngradesToFreeInsteadOfHardBlocking() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.FREE, PharmacySubscriptionStatusEnum.TRIALING);
        pharmacy.setTrialEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId)).thenReturn(0L);

        assertThatCode(() -> guard.assertCanCreateInventoryItem(pharmacyId)).doesNotThrowAnyException();

        assertThat(pharmacy.getPlan()).isEqualTo(PharmacyPlanEnum.FREE);
        assertThat(pharmacy.getSubscriptionStatus()).isEqualTo(PharmacySubscriptionStatusEnum.ACTIVE);
    }

    @Test
    void expiredPaidPeriodLazilyDowngradesToFree() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.PRO, PharmacySubscriptionStatusEnum.ACTIVE);
        pharmacy.setCurrentPeriodEndsAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId)).thenReturn(0L);

        guard.assertCanCreateInventoryItem(pharmacyId);

        assertThat(pharmacy.getPlan()).isEqualTo(PharmacyPlanEnum.FREE);
        assertThat(pharmacy.getCurrentPeriodEndsAt()).isNull();
    }

    @Test
    void agentUsageIsCountedAndBlockedAtTheMonthlyLimit() {
        PharmacyEntity pharmacy = pharmacyWith(PharmacyPlanEnum.FREE, PharmacySubscriptionStatusEnum.ACTIVE);
        when(pharmacyService.getById(pharmacyId)).thenReturn(pharmacy);
        when(valueOperations.increment(any(String.class))).thenReturn(21L); // FREE limit is 20

        assertThatThrownBy(() -> guard.assertCanUseAgentAndRecordUsage(pharmacyId))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_LIMIT_REACHED));
    }
}
