package com.pharmasense.agent.tool.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.pharmasense.agent.tool.AgentTool;
import com.pharmasense.agent.tool.AgentToolContext;
import com.pharmasense.agent.tool.JsonSchema;
import com.pharmasense.identity.enums.PermissionEnum;
import com.pharmasense.prescription.enums.PrescriptionStatusEnum;
import com.pharmasense.prescription.service.PrescriptionService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

/** "Which prescriptions are still pending?" / "show me today's filled prescriptions" */
@Component
public class LookupPrescriptionsTool implements AgentTool {

    private final PrescriptionService prescriptionService;

    public LookupPrescriptionsTool(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }

    @Override
    public String name() {
        return "lookup_prescriptions";
    }

    @Override
    public String description() {
        return "List prescriptions, optionally filtered by status.";
    }

    @Override
    public PermissionEnum requiredPermission() {
        return PermissionEnum.PRESCRIPTION_READ;
    }

    @Override
    public Map<String, Object> parameterSchema() {
        return JsonSchema.object()
                .string("status", "Optional filter: PENDING, READY, FILLED, or CANCELLED")
                .build();
    }

    @Override
    public Object execute(AgentToolContext context, JsonNode input) {
        PrescriptionStatusEnum status = input.hasNonNull("status") ? PrescriptionStatusEnum.valueOf(input.path("status").asText()) : null;
        return prescriptionService.list(context.pharmacyId(), status, PageRequest.of(0, 20)).getContent();
    }
}
