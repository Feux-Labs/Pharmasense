package com.pharmasense.admin.service;

import com.pharmasense.admin.config.AdminProperties;
import com.pharmasense.admin.dto.ImpersonationResponse;
import com.pharmasense.admin.entity.ImpersonationAuditLogEntity;
import com.pharmasense.admin.repository.ImpersonationAuditLogRepository;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.mapper.UserAccountMapper;
import com.pharmasense.identity.security.JwtService;
import com.pharmasense.identity.service.UserAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Lets a super-admin act as a specific user for support/troubleshooting.
 * The minted token carries the target user's own role and pharmacy - not
 * super-admin privileges - so an impersonating admin sees and can do exactly
 * what that user could, nothing more. Every grant is logged; see
 * {@link ImpersonationAuditLogEntity}.
 */
@Service
public class ImpersonationService {

    private static final Logger log = LoggerFactory.getLogger(ImpersonationService.class);

    private final UserAccountService userAccountService;
    private final UserAccountMapper userAccountMapper;
    private final JwtService jwtService;
    private final ImpersonationAuditLogRepository impersonationAuditLogRepository;
    private final AdminProperties adminProperties;

    public ImpersonationService(
            UserAccountService userAccountService,
            UserAccountMapper userAccountMapper,
            JwtService jwtService,
            ImpersonationAuditLogRepository impersonationAuditLogRepository,
            AdminProperties adminProperties) {
        this.userAccountService = userAccountService;
        this.userAccountMapper = userAccountMapper;
        this.jwtService = jwtService;
        this.impersonationAuditLogRepository = impersonationAuditLogRepository;
        this.adminProperties = adminProperties;
    }

    @Transactional
    public ImpersonationResponse impersonate(UUID superAdminUserId, UUID targetUserId, String reason) {
        UserAccountEntity targetUser = userAccountService.getById(targetUserId);

        ImpersonationAuditLogEntity auditEntry = new ImpersonationAuditLogEntity();
        auditEntry.setSuperAdminUserId(superAdminUserId);
        auditEntry.setTargetUserId(targetUserId);
        auditEntry.setTargetPharmacyId(targetUser.getPharmacyId());
        auditEntry.setReason(reason);
        auditEntry.setStartedAt(Instant.now());
        impersonationAuditLogRepository.save(auditEntry);

        log.info("Super-admin {} started impersonating user {} (pharmacy {})", superAdminUserId, targetUserId, targetUser.getPharmacyId());

        String accessToken = jwtService.generateAccessToken(targetUser, superAdminUserId, adminProperties.impersonationTtlMinutes());
        return new ImpersonationResponse(
                accessToken,
                adminProperties.impersonationTtlMinutes() * 60L,
                userAccountMapper.toResponse(targetUser));
    }
}
