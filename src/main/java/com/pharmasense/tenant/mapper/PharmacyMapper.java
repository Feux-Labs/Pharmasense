package com.pharmasense.tenant.mapper;

import com.pharmasense.tenant.dto.PharmacyResponse;
import com.pharmasense.tenant.dto.PharmacyUpdateRequest;
import com.pharmasense.tenant.entity.PharmacyEntity;
import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mapper;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface PharmacyMapper {

    PharmacyResponse toResponse(PharmacyEntity entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void applyUpdate(PharmacyUpdateRequest request, @MappingTarget PharmacyEntity entity);
}
