package com.pharmasense.admin.controller;

import com.pharmasense.admin.dto.PlatformAnalyticsResponse;
import com.pharmasense.admin.service.AdminAnalyticsService;
import com.pharmasense.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin / Analytics")
@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AdminAnalyticsController {

    private final AdminAnalyticsService adminAnalyticsService;

    public AdminAnalyticsController(AdminAnalyticsService adminAnalyticsService) {
        this.adminAnalyticsService = adminAnalyticsService;
    }

    @GetMapping("/overview")
    public ApiResponse<PlatformAnalyticsResponse> overview() {
        return ApiResponse.ok(adminAnalyticsService.getOverview());
    }
}
