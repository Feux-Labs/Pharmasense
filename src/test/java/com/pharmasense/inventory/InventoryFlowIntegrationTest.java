package com.pharmasense.inventory;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the inventory module's core promise: create a
 * product, receive a batch, watch its aggregate stock/expiry status update,
 * sell some of it, and hit the floor where an over-sell is rejected -
 * plus one RBAC check proving STAFF really can't do what OWNER can.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InventoryFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private EmailService emailService;

    @Test
    void receivingStockThenSellingItUpdatesAggregateStatus() throws Exception {
        String ownerToken = registerAndLogin("Apotek Melati " + UUID.randomUUID());

        Map<String, Object> createItemRequest = Map.of(
                "name", "Paracetamol 500mg",
                "category", "Pain relief",
                "unit", "strip",
                "unitSellingPrice", 12.50,
                "requiresPrescription", false,
                "lowStockThreshold", 10);

        MvcResult createResult = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createItemRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.stockLevelStatus").value("OUT_OF_STOCK"))
                .andReturn();

        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        Map<String, Object> receiveBatchRequest = Map.of(
                "batchNumber", "B-0001",
                "quantityOnHand", 100,
                "unitCostPrice", 8.00,
                "expiryDate", LocalDate.now().plusYears(1).toString());

        mockMvc.perform(post("/api/v1/inventory/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(receiveBatchRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantityOnHand").value(100));

        mockMvc.perform(get("/api/v1/inventory/items/" + itemId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalQuantityOnHand").value(100))
                .andExpect(jsonPath("$.data.stockLevelStatus").value("OK"));

        // Sell more than exists - must be rejected, and must not touch the stock count.
        Map<String, Object> overSell = Map.of("movementType", "SOLD", "quantityDelta", -500, "reason", "test over-sell");
        String batchesResponse = mockMvc.perform(get("/api/v1/inventory/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String batchId = objectMapper.readTree(batchesResponse).path("data").get(0).path("id").asText();

        mockMvc.perform(post("/api/v1/inventory/batches/" + batchId + "/adjust")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(overSell)))
                .andExpect(status().isConflict());

        // A valid sale succeeds and reduces the quantity.
        Map<String, Object> validSale = Map.of("movementType", "SOLD", "quantityDelta", -30, "reason", "counter sale");
        mockMvc.perform(post("/api/v1/inventory/batches/" + batchId + "/adjust")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validSale)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantityOnHand").value(70));
    }

    @Test
    void staffRoleCannotDeleteInventoryItems() throws Exception {
        String ownerToken = registerAndLogin("Apotek Melati " + UUID.randomUUID());
        String staffEmail = "staff+" + UUID.randomUUID() + "@example.com";

        mockMvc.perform(post("/api/v1/users/invite")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "email", staffEmail, "fullName", "Staff Member", "role", "STAFF"))))
                .andExpect(status().isCreated());

        String staffToken = loginAs(staffEmail);

        Map<String, Object> createItemRequest = Map.of(
                "name", "Test Item " + UUID.randomUUID(),
                "unitSellingPrice", 5.00,
                "requiresPrescription", false);
        MvcResult createResult = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createItemRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String itemId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        // STAFF can read...
        mockMvc.perform(get("/api/v1/inventory/items/" + itemId).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk());

        // ...but cannot delete.
        mockMvc.perform(delete("/api/v1/inventory/items/" + itemId).header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isForbidden());
    }

    /** Signs up a new pharmacy (which sends its own OTP) and verifies it in one step - never call {@link #loginAs} for the same email right after this. */
    private String registerAndLogin(String pharmacyName) throws Exception {
        String ownerEmail = "owner+" + UUID.randomUUID() + "@example.com";
        Map<String, String> signupRequest = Map.of(
                "pharmacyName", pharmacyName,
                "ownerFullName", "Test Owner",
                "ownerEmail", ownerEmail,
                "currencyCode", "USD");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        return verifyOtpAndCaptureToken(ownerEmail);
    }

    /** For an account that hasn't had an OTP sent yet this test run (e.g. a freshly-invited staff member). */
    private String loginAs(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/otp/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());

        return verifyOtpAndCaptureToken(email);
    }

    private String verifyOtpAndCaptureToken(String email) throws Exception {
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(eq(email), anyString(), codeCaptor.capture(), anyInt());
        String code = codeCaptor.getValue();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "code", code))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String accessToken = body.path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
