package com.pharmasense.sync.service;

import com.pharmasense.inventory.dto.InventoryBatchResponse;
import com.pharmasense.inventory.dto.InventoryItemResponse;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.inventory.service.InventoryItemService;
import com.pharmasense.prescription.dto.PatientResponse;
import com.pharmasense.prescription.dto.PrescriptionResponse;
import com.pharmasense.prescription.mapper.PatientMapper;
import com.pharmasense.prescription.service.PatientService;
import com.pharmasense.prescription.service.PrescriptionService;
import com.pharmasense.sync.dto.TenantSnapshotResponse;
import com.pharmasense.sync.repository.SyncChangeLogRepository;
import com.pharmasense.tenant.entity.PharmacyEntity;
import com.pharmasense.tenant.service.PharmacyService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds the full "everything this pharmacy needs to work offline" bundle.
 * The client is expected to fetch this once in the background right after
 * coming online (and periodically thereafter), cache it locally, and use
 * {@code SyncPullService} for cheap incremental catch-ups in between -
 * pulling the whole snapshot on every sync would be wasteful once a pharmacy
 * has thousands of inventory rows.
 *
 * <p>Capped at 1000 rows per entity type for this release; a pharmacy past
 * that size needs a paginated/streamed snapshot instead - see BUILD.md.
 */
@Service
public class TenantSnapshotService {

    private static final int SNAPSHOT_ROW_CAP = 1000;

    private final InventoryItemService inventoryItemService;
    private final InventoryBatchService inventoryBatchService;
    private final PatientService patientService;
    private final PatientMapper patientMapper;
    private final PrescriptionService prescriptionService;
    private final PharmacyService pharmacyService;
    private final SyncChangeLogRepository syncChangeLogRepository;

    public TenantSnapshotService(
            InventoryItemService inventoryItemService,
            InventoryBatchService inventoryBatchService,
            PatientService patientService,
            PatientMapper patientMapper,
            PrescriptionService prescriptionService,
            PharmacyService pharmacyService,
            SyncChangeLogRepository syncChangeLogRepository) {
        this.inventoryItemService = inventoryItemService;
        this.inventoryBatchService = inventoryBatchService;
        this.patientService = patientService;
        this.patientMapper = patientMapper;
        this.prescriptionService = prescriptionService;
        this.pharmacyService = pharmacyService;
        this.syncChangeLogRepository = syncChangeLogRepository;
    }

    @Cacheable(cacheNames = "tenantOfflineSnapshot", key = "#pharmacyId")
    public TenantSnapshotResponse buildSnapshot(UUID pharmacyId) {
        PharmacyEntity pharmacy = pharmacyService.getById(pharmacyId);
        Pageable cap = PageRequest.of(0, SNAPSHOT_ROW_CAP, Sort.by("createdAt").descending());

        java.util.List<InventoryItemResponse> items = inventoryItemService.list(pharmacyId, null, cap).getContent();
        java.util.List<InventoryBatchResponse> batches = inventoryBatchService.listAllForPharmacy(pharmacyId).stream()
                .map(batch -> inventoryBatchService.toResponse(batch, pharmacy.getExpiryWarningDaysDefault()))
                .limit(SNAPSHOT_ROW_CAP)
                .toList();
        java.util.List<PatientResponse> patients = patientService.list(pharmacyId, null, cap).map(patientMapper::toResponse).getContent();
        java.util.List<PrescriptionResponse> prescriptions = prescriptionService.list(pharmacyId, null, cap).getContent();

        Long maxSequence = syncChangeLogRepository.findMaxSequenceNumberByPharmacyId(pharmacyId);

        return new TenantSnapshotResponse(items, batches, patients, prescriptions, maxSequence != null ? maxSequence : 0L, Instant.now());
    }
}
