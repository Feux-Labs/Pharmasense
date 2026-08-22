package com.pharmasense.billing.entity;

import com.pharmasense.billing.enums.PaymentStatusEnum;
import com.pharmasense.common.domain.AuditableEntity;
import com.pharmasense.tenant.enums.PharmacyPlanEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * One Paystack checkout attempt. Created PENDING when checkout is
 * initialized, flipped to SUCCESS/FAILED by the webhook once Paystack
 * confirms the outcome - {@link #reference} is the join key between the two.
 */
@Getter
@Setter
@Entity
@Table(name = "payments")
public class PaymentEntity extends AuditableEntity {

    @Column(name = "pharmacy_id", nullable = false)
    private UUID pharmacyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_plan", nullable = false, length = 32)
    private PharmacyPlanEnum targetPlan;

    @Column(name = "amount_kobo", nullable = false)
    private long amountKobo;

    @Column(nullable = false, unique = true)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatusEnum status = PaymentStatusEnum.PENDING;

    @Column(name = "paystack_transaction_id")
    private String paystackTransactionId;
}
