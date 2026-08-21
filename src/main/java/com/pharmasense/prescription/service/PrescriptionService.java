package com.pharmasense.prescription.service;

import com.pharmasense.common.exception.ApiException;
import com.pharmasense.common.exception.ErrorCode;
import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.inventory.entity.InventoryBatchEntity;
import com.pharmasense.inventory.entity.InventoryItemEntity;
import com.pharmasense.inventory.enums.StockMovementTypeEnum;
import com.pharmasense.inventory.service.InventoryBatchService;
import com.pharmasense.inventory.service.InventoryItemService;
import com.pharmasense.prescription.dto.PrescriptionCreateRequest;
import com.pharmasense.prescription.dto.PrescriptionItemRequest;
import com.pharmasense.prescription.dto.PrescriptionItemResponse;
import com.pharmasense.prescription.dto.PrescriptionResponse;
import com.pharmasense.prescription.entity.PatientEntity;
import com.pharmasense.prescription.entity.PrescriptionEntity;
import com.pharmasense.prescription.entity.PrescriptionItemEntity;
import com.pharmasense.prescription.enums.PrescriptionStatusEnum;
import com.pharmasense.prescription.repository.PrescriptionItemRepository;
import com.pharmasense.prescription.repository.PrescriptionRepository;
import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;
import com.pharmasense.sync.service.SyncChangeRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Prescriptions move PENDING -&gt; READY -&gt; FILLED (or CANCELLED at any point
 * before FILLED). Filling is the interesting step: it draws inventory FEFO
 * (first-expiry-first-out) across as many batches as needed to cover the
 * prescribed quantity, and fails the whole operation atomically if there
 * isn't enough stock anywhere - a prescription is never partially filled.
 */
@Service
public class PrescriptionService {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionItemRepository prescriptionItemRepository;
    private final PatientService patientService;
    private final InventoryItemService inventoryItemService;
    private final InventoryBatchService inventoryBatchService;
    private final SyncChangeRecorder syncChangeRecorder;

    public PrescriptionService(
            PrescriptionRepository prescriptionRepository,
            PrescriptionItemRepository prescriptionItemRepository,
            PatientService patientService,
            InventoryItemService inventoryItemService,
            InventoryBatchService inventoryBatchService,
            SyncChangeRecorder syncChangeRecorder) {
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionItemRepository = prescriptionItemRepository;
        this.patientService = patientService;
        this.inventoryItemService = inventoryItemService;
        this.inventoryBatchService = inventoryBatchService;
        this.syncChangeRecorder = syncChangeRecorder;
    }

    @Transactional
    public PrescriptionResponse create(UUID pharmacyId, PrescriptionCreateRequest request) {
        PatientEntity patient = patientService.getEntity(pharmacyId, request.patientId());

        PrescriptionEntity prescription = new PrescriptionEntity();
        prescription.setPharmacyId(pharmacyId);
        prescription.setPatientId(patient.getId());
        prescription.setPrescribingDoctor(request.prescribingDoctor());
        prescription.setNotes(request.notes());
        prescription.setStatus(PrescriptionStatusEnum.PENDING);
        PrescriptionEntity savedPrescription = prescriptionRepository.save(prescription);

        for (PrescriptionItemRequest itemRequest : request.items()) {
            inventoryItemService.getEntity(pharmacyId, itemRequest.inventoryItemId()); // validates it belongs to this pharmacy
            PrescriptionItemEntity item = new PrescriptionItemEntity();
            item.setPharmacyId(pharmacyId);
            item.setPrescriptionId(savedPrescription.getId());
            item.setInventoryItemId(itemRequest.inventoryItemId());
            item.setQuantityPrescribed(itemRequest.quantityPrescribed());
            item.setDosageInstructions(itemRequest.dosageInstructions());
            prescriptionItemRepository.save(item);
        }

        PrescriptionResponse response = toResponse(savedPrescription, patient);
        syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.PRESCRIPTION, savedPrescription.getId(), SyncOperationEnum.CREATE, response);
        return response;
    }

    @Transactional
    public PrescriptionResponse updateStatus(UUID pharmacyId, UUID prescriptionId, PrescriptionStatusEnum newStatus, UUID actingUserId) {
        PrescriptionEntity prescription = getEntity(pharmacyId, prescriptionId);

        if (prescription.getStatus() == PrescriptionStatusEnum.FILLED || prescription.getStatus() == PrescriptionStatusEnum.CANCELLED) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "Cannot change status of a prescription that is already " + prescription.getStatus());
        }

        if (newStatus == PrescriptionStatusEnum.FILLED) {
            fillPrescription(prescription, actingUserId);
        } else {
            prescription.setStatus(newStatus);
            prescriptionRepository.save(prescription);
        }

        PatientEntity patient = patientService.getEntity(pharmacyId, prescription.getPatientId());
        PrescriptionResponse response = toResponse(prescription, patient);
        syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.PRESCRIPTION, prescription.getId(), SyncOperationEnum.UPDATE, response);
        return response;
    }

    private void fillPrescription(PrescriptionEntity prescription, UUID actingUserId) {
        List<PrescriptionItemEntity> items = prescriptionItemRepository.findByPrescriptionId(prescription.getId());

        for (PrescriptionItemEntity item : items) {
            drawStockFefo(prescription.getPharmacyId(), item, actingUserId);
            item.setQuantityFilled(item.getQuantityPrescribed());
            prescriptionItemRepository.save(item);
        }

        prescription.setStatus(PrescriptionStatusEnum.FILLED);
        prescription.setFilledByUserId(actingUserId);
        prescription.setFilledAt(Instant.now());
        prescriptionRepository.save(prescription);
    }

    private void drawStockFefo(UUID pharmacyId, PrescriptionItemEntity item, UUID actingUserId) {
        List<InventoryBatchEntity> availableBatches = inventoryBatchService.listAvailableBatchesFefo(item.getInventoryItemId());
        int remaining = item.getQuantityPrescribed();

        int totalAvailable = availableBatches.stream().mapToInt(InventoryBatchEntity::getQuantityOnHand).sum();
        if (totalAvailable < remaining) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT,
                    "Not enough stock to fill this prescription item (need " + remaining + ", have " + totalAvailable + ")");
        }

        for (InventoryBatchEntity batch : availableBatches) {
            if (remaining <= 0) {
                break;
            }
            int drawFromThisBatch = Math.min(remaining, batch.getQuantityOnHand());
            inventoryBatchService.adjustQuantity(
                    pharmacyId, batch.getId(), StockMovementTypeEnum.SOLD, -drawFromThisBatch,
                    "Dispensed against prescription", actingUserId);
            remaining -= drawFromThisBatch;
        }
    }

    public PrescriptionResponse getResponse(UUID pharmacyId, UUID prescriptionId) {
        PrescriptionEntity prescription = getEntity(pharmacyId, prescriptionId);
        PatientEntity patient = patientService.getEntity(pharmacyId, prescription.getPatientId());
        return toResponse(prescription, patient);
    }

    public PrescriptionEntity getEntity(UUID pharmacyId, UUID prescriptionId) {
        return prescriptionRepository.findByIdAndPharmacyId(prescriptionId, pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Prescription", prescriptionId));
    }

    public Page<PrescriptionResponse> list(UUID pharmacyId, PrescriptionStatusEnum status, Pageable pageable) {
        Page<PrescriptionEntity> page = status != null
                ? prescriptionRepository.findByPharmacyIdAndStatus(pharmacyId, status, pageable)
                : prescriptionRepository.findByPharmacyId(pharmacyId, pageable);
        return page.map(prescription -> toResponse(prescription, patientService.getEntity(pharmacyId, prescription.getPatientId())));
    }

    private PrescriptionResponse toResponse(PrescriptionEntity prescription, PatientEntity patient) {
        List<PrescriptionItemResponse> itemResponses = prescriptionItemRepository.findByPrescriptionId(prescription.getId()).stream()
                .map(item -> {
                    InventoryItemEntity inventoryItem = inventoryItemService.getEntity(prescription.getPharmacyId(), item.getInventoryItemId());
                    return new PrescriptionItemResponse(
                            item.getId(), item.getInventoryItemId(), inventoryItem.getName(),
                            item.getQuantityPrescribed(), item.getQuantityFilled(), item.getDosageInstructions());
                })
                .toList();

        return new PrescriptionResponse(
                prescription.getId(),
                patient.getId(),
                patient.getFullName(),
                prescription.getPrescribingDoctor(),
                prescription.getStatus(),
                prescription.getNotes(),
                prescription.getFilledByUserId(),
                prescription.getFilledAt(),
                itemResponses,
                prescription.getCreatedAt());
    }
}
