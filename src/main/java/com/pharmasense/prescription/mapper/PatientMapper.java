package com.pharmasense.prescription.mapper;

import com.pharmasense.prescription.dto.PatientResponse;
import com.pharmasense.prescription.entity.PatientEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PatientMapper {

    PatientResponse toResponse(PatientEntity entity);
}
