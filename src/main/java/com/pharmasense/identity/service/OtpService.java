package com.pharmasense.identity.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.config.OtpProperties;
import com.pharmasense.identity.security.TokenHasher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * One-time login codes live only in Redis, never in Postgres - they're
 * short-lived by design and there's no value in persisting them durably.
 * Storage format per email: {@code otp:{email} -> "<sha256(code)>:<attempts>"}
 * with a TTL equal to {@code pharmasense.otp.ttl-minutes}.
 */
@Service
public class OtpService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_KEY_PREFIX = "otp:code:";
    private static final String COOLDOWN_KEY_PREFIX = "otp:cooldown:";

    private final StringRedisTemplate redisTemplate;
    private final TokenHasher tokenHasher;
    private final OtpProperties otpProperties;

    public OtpService(StringRedisTemplate redisTemplate, TokenHasher tokenHasher, OtpProperties otpProperties) {
        this.redisTemplate = redisTemplate;
        this.tokenHasher = tokenHasher;
        this.otpProperties = otpProperties;
    }

    public String generateAndStore(String email) {
        String cooldownKey = COOLDOWN_KEY_PREFIX + email.toLowerCase();
        if (Boolean.TRUE.equals(redisTemplate.hasKey(cooldownKey))) {
            throw new ApiException(ErrorCode.OTP_RATE_LIMITED,
                    "Please wait before requesting another code");
        }

        String code = generateNumericCode(otpProperties.length());
        String otpKey = OTP_KEY_PREFIX + email.toLowerCase();
        redisTemplate.opsForValue().set(otpKey, tokenHasher.sha256(code) + ":0", Duration.ofMinutes(otpProperties.ttlMinutes()));
        redisTemplate.opsForValue().set(cooldownKey, "1", Duration.ofSeconds(otpProperties.resendCooldownSeconds()));
        return code;
    }

    /** @throws ApiException with {@code OTP_INVALID_OR_EXPIRED} if the code is wrong, expired, or attempts are exhausted */
    public void verify(String email, String submittedCode) {
        String otpKey = OTP_KEY_PREFIX + email.toLowerCase();
        String stored = redisTemplate.opsForValue().get(otpKey);
        if (stored == null) {
            throw new ApiException(ErrorCode.OTP_INVALID_OR_EXPIRED, "Code has expired. Please request a new one.");
        }

        String[] parts = stored.split(":", 2);
        String storedHash = parts[0];
        int attempts = Integer.parseInt(parts[1]);

        if (attempts >= otpProperties.maxVerifyAttempts()) {
            redisTemplate.delete(otpKey);
            throw new ApiException(ErrorCode.OTP_INVALID_OR_EXPIRED, "Too many incorrect attempts. Please request a new code.");
        }

        if (!storedHash.equals(tokenHasher.sha256(submittedCode))) {
            Duration remainingTtl = redisTemplate.getExpire(otpKey) != null
                    ? Duration.ofSeconds(redisTemplate.getExpire(otpKey))
                    : Duration.ofMinutes(otpProperties.ttlMinutes());
            redisTemplate.opsForValue().set(otpKey, storedHash + ":" + (attempts + 1), remainingTtl);
            throw new ApiException(ErrorCode.OTP_INVALID_OR_EXPIRED, "Incorrect code");
        }

        redisTemplate.delete(otpKey);
    }

    private String generateNumericCode(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(SECURE_RANDOM.nextInt(10));
        }
        return builder.toString();
    }
}
