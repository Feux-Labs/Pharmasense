package com.pharmasense.identity.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.dto.InviteStaffRequest;
import com.pharmasense.identity.dto.UserResponse;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.mapper.UserAccountMapper;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.identity.service.UserAccountService;
import com.pharmasense.notification.service.EmailService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/v1/users")
public class UserAccountController {

    private final UserAccountService userAccountService;
    private final UserAccountMapper userAccountMapper;
    private final EmailService emailService;
    private final String frontendBaseUrl;

    public UserAccountController(
            UserAccountService userAccountService,
            UserAccountMapper userAccountMapper,
            EmailService emailService,
            @Value("${pharmasense.frontend.base-url}") String frontendBaseUrl) {
        this.userAccountService = userAccountService;
        this.userAccountMapper = userAccountMapper;
        this.emailService = emailService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> getCurrentUser(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        UserAccountEntity user = userAccountService.getById(principal.userId());
        return ApiResponse.ok(userAccountMapper.toResponse(user));
    }

    @GetMapping
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'STAFF_MANAGE')")
    public ApiResponse<List<UserResponse>> listStaff(@AuthenticationPrincipal PharmasenseUserPrincipal principal) {
        List<UserResponse> staff = userAccountService.listStaffForPharmacy(principal.pharmacyId()).stream()
                .map(userAccountMapper::toResponse)
                .toList();
        return ApiResponse.ok(staff);
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("@rbacEvaluator.hasPermission(authentication, 'STAFF_MANAGE')")
    public ApiResponse<UserResponse> inviteStaffMember(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @Valid @RequestBody InviteStaffRequest request) {
        UserAccountEntity invited = userAccountService.inviteStaffMember(
                principal.pharmacyId(), request.email(), request.fullName(), request.role());
        String inviteUrl = frontendBaseUrl + "/login?invited=" + invited.getEmail();
        emailService.sendStaffInvite(invited.getEmail(), invited.getFullName(), "your pharmacy", inviteUrl);
        return ApiResponse.ok(userAccountMapper.toResponse(invited), "Invitation sent");
    }
}
