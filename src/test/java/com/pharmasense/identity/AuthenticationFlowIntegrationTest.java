package com.pharmasense.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasense.TestcontainersConfiguration;
import com.pharmasense.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the whole email + OTP auth flow end to end against a real
 * Postgres and Redis (via Testcontainers): sign up a new pharmacy, receive
 * the OTP "email" (intercepted here instead of actually sent - see
 * application-test.yml), verify it, and use the resulting token against a
 * protected endpoint.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthenticationFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private EmailService emailService;

    @Test
    void signUpVerifyOtpAndAccessProtectedEndpoint() throws Exception {
        String email = "owner+" + UUID.randomUUID() + "@example.com";

        Map<String, String> signupRequest = Map.of(
                "pharmacyName", "Test Pharmacy",
                "ownerFullName", "Jane Owner",
                "ownerEmail", email,
                "currencyCode", "USD");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(eq(email), anyString(), codeCaptor.capture(), anyInt());
        String code = codeCaptor.getValue();

        Map<String, String> verifyRequest = Map.of("email", email, "code", code, "deviceLabel", "integration-test");

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").exists())
                .andExpect(jsonPath("$.data.refreshToken").exists())
                .andExpect(jsonPath("$.data.user.role").value("OWNER"))
                .andReturn();

        String accessToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();

        mockMvc.perform(get("/api/v1/inventory/items").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    @Test
    void wrongOtpCodeIsRejected() throws Exception {
        String email = "owner+" + UUID.randomUUID() + "@example.com";
        Map<String, String> signupRequest = Map.of(
                "pharmacyName", "Test Pharmacy",
                "ownerFullName", "Jane Owner",
                "ownerEmail", email,
                "currencyCode", "USD");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        Map<String, String> verifyRequest = Map.of("email", email, "code", "000000", "deviceLabel", "integration-test");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsMissingToken() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/items"))
                .andExpect(status().isUnauthorized());
    }
}
