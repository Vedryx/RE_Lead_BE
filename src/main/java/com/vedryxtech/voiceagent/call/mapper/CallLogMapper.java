package com.vedryxtech.voiceagent.call.mapper;

import com.vedryxtech.voiceagent.mapper.MapperConfiguration;
import com.vedryxtech.voiceagent.mapper.MapperSupport;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.call.api.dto.CallLogResponse;
import com.vedryxtech.voiceagent.call.api.dto.CallSessionResponse;
import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfiguration.class, uses = MapperSupport.class)
public interface CallLogMapper {

    @Mapping(target = "id", source = "idAsString")
    @Mapping(target = "leadId", source = "leadIdAsString")
    CallLogResponse toResponse(LeadCallLog callLog);

    @Mapping(target = "callLogId", source = "callLog.idAsString")
    @Mapping(target = "leadId", source = "lead.idAsString")
    @Mapping(target = "phone", source = "lead.callingPhone")
    @Mapping(target = "name", source = "lead.name")
    @Mapping(target = "attemptNumber", source = "callLog.attemptNumber", qualifiedByName = "integerToInt")
    CallSessionResponse toResponse(CallOrchestrationService.CallSession session);
}
