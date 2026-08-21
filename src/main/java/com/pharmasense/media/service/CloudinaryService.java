package com.pharmasense.media.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;

    private final Cloudinary cloudinary;

    public CloudinaryService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    /**
     * Uploads a profile picture, replacing any previous one for the same
     * user (same {@code public_id}, so old versions don't pile up in the
     * Cloudinary account). Returns the HTTPS URL to store on the user record.
     */
    public String uploadProfilePicture(MultipartFile file, UUID userId) {
        validate(file);

        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "pharmasense/avatars",
                    "public_id", userId.toString(),
                    "overwrite", true,
                    "resource_type", "image",
                    "transformation", ObjectUtils.asMap("width", 256, "height", 256, "crop", "fill", "gravity", "face")));
            return (String) result.get("secure_url");
        } catch (IOException e) {
            log.error("Cloudinary upload failed for user {}", userId, e);
            throw new ApiException(ErrorCode.UPSTREAM_SERVICE_UNAVAILABLE, "Couldn't upload the image. Please try again.", e);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "No file was provided");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Image must be 5MB or smaller");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Image must be JPEG, PNG, or WebP");
        }
    }
}
