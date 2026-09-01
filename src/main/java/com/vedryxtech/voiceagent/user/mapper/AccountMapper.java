package com.vedryxtech.voiceagent.user.mapper;

import com.vedryxtech.voiceagent.mapper.MapperConfiguration;
import com.vedryxtech.voiceagent.organization.domain.Organization;
import com.vedryxtech.voiceagent.user.domain.User;
import com.vedryxtech.voiceagent.organization.api.dto.OrganizationResponse;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps tenancy documents to their API shapes. The password hash never leaves the service layer. */
@Mapper(config = MapperConfiguration.class)
public interface AccountMapper {

    @Mapping(target = "id", source = "idAsString")
    UserResponse toResponse(User user);

    @Mapping(target = "id", source = "idAsString")
    OrganizationResponse toResponse(Organization organization);
}
