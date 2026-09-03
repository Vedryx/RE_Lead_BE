package com.vedryxtech.voiceagent.user.mapper;

import com.vedryxtech.voiceagent.mapper.MapperConfiguration;
import com.vedryxtech.voiceagent.user.api.dto.UserResponse;
import com.vedryxtech.voiceagent.user.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps the user document to its API shape. The password hash never leaves the service layer. */
@Mapper(config = MapperConfiguration.class)
public interface AccountMapper {

    @Mapping(target = "id", source = "idAsString")
    UserResponse toResponse(User user);
}
