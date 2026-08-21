package com.pharmasense.media.controller;

import com.pharmasense.common.response.ApiResponse;
import com.pharmasense.identity.entity.UserAccountEntity;
import com.pharmasense.identity.security.PharmasenseUserPrincipal;
import com.pharmasense.identity.service.UserAccountService;
import com.pharmasense.media.dto.AvatarUploadResponse;
import com.pharmasense.media.service.CloudinaryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Media")
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final CloudinaryService cloudinaryService;
    private final UserAccountService userAccountService;

    public MediaController(CloudinaryService cloudinaryService, UserAccountService userAccountService) {
        this.cloudinaryService = cloudinaryService;
        this.userAccountService = userAccountService;
    }

    @PostMapping(value = "/profile-picture", consumes = "multipart/form-data")
    public ApiResponse<AvatarUploadResponse> uploadProfilePicture(
            @AuthenticationPrincipal PharmasenseUserPrincipal principal,
            @RequestParam("file") MultipartFile file) {
        String avatarUrl = cloudinaryService.uploadProfilePicture(file, principal.userId());

        UserAccountEntity user = userAccountService.getById(principal.userId());
        user.setAvatarUrl(avatarUrl);
        userAccountService.save(user);

        return ApiResponse.ok(new AvatarUploadResponse(avatarUrl));
    }
}
