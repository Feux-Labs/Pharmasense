package com.pharmasense.identity.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.identity.config.OtpProperties;
import com.pharmasense.identity.enums.OtpPurpose;
import com.pharmasense.identity.security.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Exercises OtpService against an in-memory fake of the two Redis
 * operations it uses, so the OTP hashing/attempt-limiting/cooldown logic is
 * verified without needing a real Redis instance.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final Map<String, String> store = new HashMap<>();
    private final TokenHasher tokenHasher = new TokenHasher();
    private final OtpProperties otpProperties = new OtpProperties(6, 10, 5, 45);

    private OtpService otpService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        when(valueOperations.get(anyString())).thenAnswer(invocation -> store.get((String) invocation.getArgument(0)));
        when(redisTemplate.hasKey(anyString())).thenAnswer(invocation -> store.containsKey((String) invocation.getArgument(0)));
        when(redisTemplate.getExpire(anyString())).thenReturn(600L);
        doAnswer(invocation -> store.remove((String) invocation.getArgument(0)) != null)
                .when(redisTemplate).delete(anyString());

        otpService = new OtpService(redisTemplate, tokenHasher, otpProperties);
    }

    @Test
    void correctCodeVerifiesSuccessfully() {
        String code = otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN);
        otpService.verify("owner@example.com", code, OtpPurpose.LOGIN);
        // no exception means success; code should be single-use now
        assertThatThrownBy(() -> otpService.verify("owner@example.com", code, OtpPurpose.LOGIN))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID_OR_EXPIRED));
    }

    @Test
    void incorrectCodeIsRejected() {
        otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN);
        assertThatThrownBy(() -> otpService.verify("owner@example.com", "000000", OtpPurpose.LOGIN))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID_OR_EXPIRED));
    }

    @Test
    void verifyingWithNoCodeRequestedIsRejected() {
        assertThatThrownBy(() -> otpService.verify("nobody@example.com", "123456", OtpPurpose.LOGIN))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID_OR_EXPIRED));
    }

    @Test
    void requestingASecondCodeBeforeCooldownExpiresIsRateLimited() {
        otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN);
        assertThatThrownBy(() -> otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_RATE_LIMITED));
    }

    @Test
    void generatedCodeHasConfiguredLength() {
        String code = otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN);
        assertThat(code).hasSize(otpProperties.length());
        assertThat(code).matches("\\d+");
    }

    @Test
    void codeIsScopedToItsPurposeAndCannotBeUsedForAnother() {
        String code = otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN);
        assertThatThrownBy(() -> otpService.verify("owner@example.com", code, OtpPurpose.PASSWORD_RESET))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode()).isEqualTo(ErrorCode.OTP_INVALID_OR_EXPIRED));
    }

    @Test
    void loginAndPasswordResetCooldownsAreIndependent() {
        otpService.generateAndStore("owner@example.com", OtpPurpose.LOGIN);
        // Should not throw - a pending LOGIN cooldown must not block PASSWORD_RESET.
        otpService.generateAndStore("owner@example.com", OtpPurpose.PASSWORD_RESET);
    }
}
