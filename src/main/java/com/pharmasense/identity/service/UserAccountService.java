package com.pharmasense.identity.service;

import com.pharmasense.common.exception.ConflictException;
import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.enums.AuthProviderEnum;
import com.pharmasense.identity.enums.UserRoleEnum;
import com.pharmasense.identity.enums.UserStatusEnum;
import com.pharmasense.identity.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;

    public UserAccountService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional
    public UserAccountEntity createSuperAdminAccount(String email, String fullName) {
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email);
        }
        UserAccountEntity admin = new UserAccountEntity();
        admin.setEmail(email.toLowerCase());
        admin.setFullName(fullName);
        admin.setRole(UserRoleEnum.SUPER_ADMIN);
        admin.setAuthProvider(AuthProviderEnum.LOCAL_OTP);
        admin.setStatus(UserStatusEnum.ACTIVE);
        return userAccountRepository.save(admin);
    }

    @Transactional
    public UserAccountEntity createOwnerAccount(UUID pharmacyId, String email, String fullName, String passwordHash) {
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email);
        }
        UserAccountEntity owner = new UserAccountEntity();
        owner.setPharmacyId(pharmacyId);
        owner.setEmail(email.toLowerCase());
        owner.setFullName(fullName);
        owner.setPasswordHash(passwordHash);
        owner.setRole(UserRoleEnum.OWNER);
        owner.setAuthProvider(AuthProviderEnum.LOCAL_OTP);
        // Password sign-up proves nothing about the mailbox, but blocking
        // login on an OTP click would defeat "sign in with password, no
        // code" - status starts ACTIVE and email verification can catch up
        // later via a separate, non-blocking flow.
        owner.setStatus(UserStatusEnum.ACTIVE);
        return userAccountRepository.save(owner);
    }

    @Transactional
    public UserAccountEntity inviteStaffMember(UUID pharmacyId, String email, String fullName, UserRoleEnum role) {
        if (role == UserRoleEnum.SUPER_ADMIN) {
            throw new ConflictException("Cannot assign the platform admin role to a pharmacy staff member");
        }
        if (userAccountRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account already exists for " + email);
        }
        UserAccountEntity staff = new UserAccountEntity();
        staff.setPharmacyId(pharmacyId);
        staff.setEmail(email.toLowerCase());
        staff.setFullName(fullName);
        staff.setRole(role);
        staff.setAuthProvider(AuthProviderEnum.LOCAL_OTP);
        staff.setStatus(UserStatusEnum.PENDING_VERIFICATION);
        return userAccountRepository.save(staff);
    }

    @Transactional
    public UserAccountEntity findOrCreateGoogleUser(String email, String fullName, String googleSubjectId) {
        UserAccountEntity user = userAccountRepository.findByGoogleSubjectId(googleSubjectId)
                .or(() -> userAccountRepository.findByEmailIgnoreCase(email).map(existing -> {
                    existing.setGoogleSubjectId(googleSubjectId);
                    return existing;
                }))
                .orElseGet(() -> {
                    UserAccountEntity newUser = new UserAccountEntity();
                    newUser.setEmail(email.toLowerCase());
                    newUser.setFullName(fullName);
                    newUser.setGoogleSubjectId(googleSubjectId);
                    newUser.setRole(UserRoleEnum.OWNER);
                    newUser.setAuthProvider(AuthProviderEnum.GOOGLE);
                    newUser.setStatus(UserStatusEnum.PENDING_VERIFICATION);
                    return newUser;
                });
        return userAccountRepository.save(user);
    }

    public UserAccountEntity getById(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    public UserAccountEntity getByEmail(String email) {
        return userAccountRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    public List<UserAccountEntity> listStaffForPharmacy(UUID pharmacyId) {
        return userAccountRepository.findByPharmacyId(pharmacyId);
    }

    @Transactional
    public void markLoggedIn(UserAccountEntity user) {
        user.setLastLoginAt(Instant.now());
        if (user.getStatus() == UserStatusEnum.PENDING_VERIFICATION) {
            user.setStatus(UserStatusEnum.ACTIVE);
        }
        userAccountRepository.save(user);
    }

    @Transactional
    public UserAccountEntity save(UserAccountEntity user) {
        return userAccountRepository.save(user);
    }
}
