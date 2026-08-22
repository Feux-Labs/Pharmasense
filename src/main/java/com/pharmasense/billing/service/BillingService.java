package com.pharmasense.billing.service;

import com.pharmasense.billing.PlanLimits;
import com.pharmasense.billing.client.PaystackClient;
import com.pharmasense.billing.config.PaystackProperties;
import com.pharmasense.billing.dto.CheckoutResponse;
import com.pharmasense.billing.dto.PlanLimitsResponse;
import com.pharmasense.billing.dto.PlanUsageResponse;
import com.pharmasense.billing.dto.SubscriptionResponse;
import com.pharmasense.billing.entity.PaymentEntity;
import com.pharmasense.billing.enums.PaymentStatusEnum;
import com.pharmasense.billing.repository.PaymentRepository;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.repository.UserAccountRepository;
import com.pharmasense.inventory.repository.InventoryItemRepository;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import com.pharmasense.tenant.enums.PharmacySubscriptionStatusEnum;
import com.pharmasense.tenant.service.PharmacyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final Duration PAID_PERIOD_LENGTH = Duration.ofDays(30);

    private final PharmacyService pharmacyService;
    private final PaymentRepository paymentRepository;
    private final PaystackClient paystackClient;
    private final PaystackProperties paystackProperties;
    private final SubscriptionGuardService subscriptionGuardService;
    private final InventoryItemRepository inventoryItemRepository;
    private final UserAccountRepository userAccountRepository;

    public BillingService(
            PharmacyService pharmacyService,
            PaymentRepository paymentRepository,
            PaystackClient paystackClient,
            PaystackProperties paystackProperties,
            SubscriptionGuardService subscriptionGuardService,
            InventoryItemRepository inventoryItemRepository,
            UserAccountRepository userAccountRepository) {
        this.pharmacyService = pharmacyService;
        this.paymentRepository = paymentRepository;
        this.paystackClient = paystackClient;
        this.paystackProperties = paystackProperties;
        this.subscriptionGuardService = subscriptionGuardService;
        this.inventoryItemRepository = inventoryItemRepository;
        this.userAccountRepository = userAccountRepository;
    }

    public SubscriptionResponse getSubscription(UUID pharmacyId) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        PlanLimits limits = PlanLimits.of(pharmacy.getPlan());
        PlanUsageResponse usage = new PlanUsageResponse(
                inventoryItemRepository.countByPharmacyIdAndActiveTrue(pharmacyId),
                userAccountRepository.countByPharmacyId(pharmacyId),
                subscriptionGuardService.currentAgentMessageCount(pharmacyId));

        return new SubscriptionResponse(
                pharmacy.getPlan(),
                pharmacy.getSubscriptionStatus(),
                pharmacy.getTrialEndsAt(),
                pharmacy.getCurrentPeriodEndsAt(),
                pharmacy.isComplimentary(),
                new PlanLimitsResponse(limits.maxInventoryItems(), limits.maxStaffUsers(), limits.maxAgentMessagesPerMonth()),
                usage);
    }

    @Transactional
    public CheckoutResponse startCheckout(UUID pharmacyId, PharmacyPlanEnum targetPlan) {
        if (targetPlan != PharmacyPlanEnum.BASIC && targetPlan != PharmacyPlanEnum.PRO) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Only BASIC and PRO plans can be purchased through checkout");
        }
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);

        long amountKobo = targetPlan == PharmacyPlanEnum.BASIC ? paystackProperties.basicPriceKobo() : paystackProperties.proPriceKobo();
        String reference = "pharmasense-" + UUID.randomUUID();

        PaymentEntity payment = new PaymentEntity();
        payment.setPharmacyId(pharmacyId);
        payment.setTargetPlan(targetPlan);
        payment.setAmountKobo(amountKobo);
        payment.setReference(reference);
        paymentRepository.save(payment);

        PaystackClient.InitializeResult result = paystackClient.initializeTransaction(
                pharmacy.getContactEmail(), amountKobo, reference,
                Map.of("pharmacyId", pharmacyId.toString(), "targetPlan", targetPlan.name()));

        return new CheckoutResponse(result.authorizationUrl(), result.reference());
    }

    /**
     * Only {@code charge.success} is handled - Paystack sends other event
     * types (e.g. transfer events) that don't apply to a one-time checkout
     * flow, and those are silently ignored rather than erroring.
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public void handleWebhookEvent(Map<String, Object> event) {
        String eventType = (String) event.get("event");
        if (!"charge.success".equals(eventType)) {
            log.info("Ignoring Paystack webhook event type: {}", eventType);
            return;
        }

        Map<String, Object> data = (Map<String, Object>) event.get("data");
        String reference = (String) data.get("reference");
        String status = (String) data.get("status");
        Object transactionId = data.get("id");

        PaymentEntity payment = paymentRepository.findByReference(reference).orElse(null);
        if (payment == null) {
            log.warn("Paystack webhook referenced an unknown payment: {}", reference);
            return;
        }
        if (payment.getStatus() != PaymentStatusEnum.PENDING) {
            log.info("Payment {} already processed as {}, ignoring duplicate webhook delivery", reference, payment.getStatus());
            return;
        }

        if (!"success".equals(status)) {
            payment.setStatus(PaymentStatusEnum.FAILED);
            paymentRepository.save(payment);
            return;
        }

        payment.setStatus(PaymentStatusEnum.SUCCESS);
        payment.setPaystackTransactionId(transactionId == null ? null : String.valueOf(transactionId));
        paymentRepository.save(payment);

        PharmacyEntity pharmacy = pharmacyService.getById(payment.getPharmacyId());
        pharmacy.setPlan(payment.getTargetPlan());
        pharmacy.setSubscriptionStatus(PharmacySubscriptionStatusEnum.ACTIVE);
        pharmacy.setCurrentPeriodEndsAt(Instant.now().plus(PAID_PERIOD_LENGTH));
        pharmacyService.save(pharmacy);
    }

    /**
     * There's no recurring auto-charge in this model (each period is a
     * separate manual checkout), so "cancel" just means "downgrade me to
     * FREE right now" rather than "stop renewing" - CANCELLED is reserved
     * for an operator manually suspending a pharmacy, which does hard-block
     * access (see {@link SubscriptionGuardService#requireUsableSubscription}).
     */
    @Transactional
    public void cancelSubscription(UUID pharmacyId) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        if (pharmacy.isComplimentary()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Complimentary accounts don't have a billable subscription to cancel");
        }
        pharmacy.setPlan(PharmacyPlanEnum.FREE);
        pharmacy.setSubscriptionStatus(PharmacySubscriptionStatusEnum.ACTIVE);
        pharmacy.setCurrentPeriodEndsAt(null);
        pharmacyService.save(pharmacy);
    }
}
