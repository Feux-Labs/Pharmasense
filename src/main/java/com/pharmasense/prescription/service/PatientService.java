package com.pharmasense.prescription.service;

import com.pharmasense.common.exception.ResourceNotFoundException;
import com.pharmasense.prescription.dto.PatientCreateRequest;
import com.pharmasense.prescription.entity.PatientEntity;
import com.pharmasense.prescription.mapper.PatientMapper;
import com.pharmasense.prescription.repository.PatientRepository;
import com.pharmasense.sync.enums.SyncEntityTypeEnum;
import com.pharmasense.sync.enums.SyncOperationEnum;
import com.pharmasense.sync.service.SyncChangeRecorder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientMapper patientMapper;
    private final SyncChangeRecorder syncChangeRecorder;

    public PatientService(PatientRepository patientRepository, PatientMapper patientMapper, SyncChangeRecorder syncChangeRecorder) {
        this.patientRepository = patientRepository;
        this.patientMapper = patientMapper;
        this.syncChangeRecorder = syncChangeRecorder;
    }

    @Transactional
    public PatientEntity create(UUID pharmacyId, PatientCreateRequest request) {
        PatientEntity patient = new PatientEntity();
        patient.setPharmacyId(pharmacyId);
        patient.setFullName(request.fullName());
        patient.setPhoneNumber(request.phoneNumber());
        patient.setDateOfBirth(request.dateOfBirth());
        patient.setNotes(request.notes());
        PatientEntity saved = patientRepository.save(patient);
        syncChangeRecorder.record(pharmacyId, SyncEntityTypeEnum.PATIENT, saved.getId(), SyncOperationEnum.CREATE, patientMapper.toResponse(saved));
        return saved;
    }

    public PatientEntity getEntity(UUID pharmacyId, UUID patientId) {
        return patientRepository.findByIdAndPharmacyId(patientId, pharmacyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patient", patientId));
    }

    public Page<PatientEntity> list(UUID pharmacyId, String searchTerm, Pageable pageable) {
        return StringUtils.hasText(searchTerm)
                ? patientRepository.findByPharmacyIdAndFullNameContainingIgnoreCase(pharmacyId, searchTerm, pageable)
                : patientRepository.findByPharmacyId(pharmacyId, pageable);
    }
}
