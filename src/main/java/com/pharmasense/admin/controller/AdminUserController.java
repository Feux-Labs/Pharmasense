package com.pharmasense.admin.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.dto.UserResponse;
import com.pharmasense.identity.mapper.UserAccountMapper;
import com.pharmasense.identity.service.UserAccountService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Platform-side view of every user account, across every pharmacy. Restricted to SUPER_ADMIN by the path rule in SecurityConfig. */
@Tag(name = "Admin / Users")
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private final UserAccountService userAccountService;
    private final UserAccountMapper userAccountMapper;

    public AdminUserController(UserAccountService userAccountService, UserAccountMapper userAccountMapper) {
        this.userAccountService = userAccountService;
        this.userAccountMapper = userAccountMapper;
    }

    @GetMapping
    public ApiResponse<List<UserResponse>> listByPharmacy(@RequestParam UUID pharmacyId) {
        List<UserResponse> users = userAccountService.listStaffForPharmacy(pharmacyId).stream()
                .map(userAccountMapper::toResponse)
                .toList();
        return ApiResponse.ok(users);
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserResponse> getById(@PathVariable UUID userId) {
        return ApiResponse.ok(userAccountMapper.toResponse(userAccountService.getById(userId)));
    }
}
