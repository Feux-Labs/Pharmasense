package com.pharmasense.prescription;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pharmasense.TestcontainersConfiguration;
import com.pharmasense.notification.service.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the trickiest part of the prescription module: filling a
 * prescription draws stock FEFO (first-expiry-first-out) across as many
 * batches as needed, and the whole fill fails atomically if there isn't
 * enough stock anywhere.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrescriptionFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoSpyBean
    private EmailService emailService;

    @Test
    void fillingAPrescriptionDrawsTheSoonestExpiringBatchFirst() throws Exception {
        String ownerToken = registerAndLogin();

        String itemId = createInventoryItem(ownerToken, "Amoxicillin 500mg");

        // Two batches: the later-received one expires sooner, so FEFO should draw from it first.
        String soonExpiringBatchId = receiveBatch(ownerToken, itemId, "B-SOON", 20, LocalDate.now().plusDays(30));
        receiveBatch(ownerToken, itemId, "B-LATER", 20, LocalDate.now().plusDays(365));

        String patientId = createPatient(ownerToken, "John Patient");

        Map<String, Object> prescriptionRequest = Map.of(
                "patientId", patientId,
                "prescribingDoctor", "Dr. Smith",
                "items", List.of(Map.of(
                        "inventoryItemId", itemId,
                        "quantityPrescribed", 15,
                        "dosageInstructions", "1 tablet twice daily")));

        MvcResult createResult = mockMvc.perform(post("/api/v1/prescriptions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prescriptionRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        String prescriptionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescriptionId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FILLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FILLED"))
                .andExpect(jsonPath("$.data.items[0].quantityFilled").value(15));

        // The soonest-expiring batch (20 units) should have been drawn down by 15, leaving 5.
        String batchesResponse = mockMvc.perform(get("/api/v1/inventory/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode batches = objectMapper.readTree(batchesResponse).path("data");
        JsonNode soonBatch = findBatchById(batches, soonExpiringBatchId);
        assertThat(soonBatch.path("quantityOnHand").asInt()).isEqualTo(5);
    }

    @Test
    void fillingAPrescriptionWithInsufficientStockIsRejectedAtomically() throws Exception {
        String ownerToken = registerAndLogin();
        String itemId = createInventoryItem(ownerToken, "Insulin Vial");
        receiveBatch(ownerToken, itemId, "B-0001", 3, LocalDate.now().plusDays(60));
        String patientId = createPatient(ownerToken, "Jane Patient");

        Map<String, Object> prescriptionRequest = Map.of(
                "patientId", patientId,
                "items", List.of(Map.of("inventoryItemId", itemId, "quantityPrescribed", 10)));

        MvcResult createResult = mockMvc.perform(post("/api/v1/prescriptions")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(prescriptionRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String prescriptionId = objectMapper.readTree(createResult.getResponse().getContentAsString()).path("data").path("id").asText();

        mockMvc.perform(patch("/api/v1/prescriptions/" + prescriptionId + "/status")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", "FILLED"))))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/prescriptions/" + prescriptionId).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    private JsonNode findBatchById(JsonNode batches, String batchId) {
        for (JsonNode batch : batches) {
            if (batch.path("id").asText().equals(batchId)) {
                return batch;
            }
        }
        throw new AssertionError("Batch " + batchId + " not found in response");
    }

    private String createInventoryItem(String ownerToken, String name) throws Exception {
        Map<String, Object> request = Map.of("name", name, "unitSellingPrice", 20.00, "requiresPrescription", true);
        MvcResult result = mockMvc.perform(post("/api/v1/inventory/items")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String receiveBatch(String ownerToken, String itemId, String batchNumber, int quantity, LocalDate expiryDate) throws Exception {
        Map<String, Object> request = Map.of(
                "batchNumber", batchNumber, "quantityOnHand", quantity, "expiryDate", expiryDate.toString());
        MvcResult result = mockMvc.perform(post("/api/v1/inventory/items/" + itemId + "/batches")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String createPatient(String ownerToken, String fullName) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/patients")
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("fullName", fullName))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private String registerAndLogin() throws Exception {
        String ownerEmail = "owner+" + UUID.randomUUID() + "@example.com";
        Map<String, String> signupRequest = Map.of(
                "pharmacyName", "Test Pharmacy " + UUID.randomUUID(),
                "ownerFullName", "Test Owner",
                "ownerEmail", ownerEmail,
                "currencyCode", "USD");

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(signupRequest)))
                .andExpect(status().isCreated());

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendOtpCode(eq(ownerEmail), anyString(), codeCaptor.capture(), anyInt());
        String code = codeCaptor.getValue();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", ownerEmail, "code", code))))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("accessToken").asText();
        assertThat(accessToken).isNotBlank();
        return accessToken;
    }
}
