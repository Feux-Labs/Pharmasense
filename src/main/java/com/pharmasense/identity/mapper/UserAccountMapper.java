package com.pharmasense.identity.mapper;

import com.pharmasense.identity.dto.UserResponse;
import com.pharmasense.identity.entity.UserAccountEntity;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserAccountMapper {

    UserResponse toResponse(UserAccountEntity entity);
}
