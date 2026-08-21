package com.pharmasense.sync.dto;

import com.pharmasense.inventory.dto.InventoryBatchResponse;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.prescription.dto.PatientResponse;
import com.pharmasense.prescription.dto.PrescriptionResponse;

import java.time.Instant;
import java.util.List;

/**
 * The full "download everything for offline use" bundle - what the client
 * fetches in the background as soon as it's online and caches locally
 * (IndexedDB), so the app stays usable with no connection. {@code asOfSequence}
 * is the change-log cursor at the moment this snapshot was built; the client
 * should store it and use it as the {@code since} value for its first
 * incremental pull, rather than re-downloading the whole snapshot again.
 */
public record TenantSnapshotResponse(
        List<InventoryItemResponse> inventoryItems,
        List<InventoryBatchResponse> inventoryBatches,
        List<PatientResponse> patients,
        List<PrescriptionResponse> prescriptions,
        long asOfSequence,
        Instant generatedAt) {
}
