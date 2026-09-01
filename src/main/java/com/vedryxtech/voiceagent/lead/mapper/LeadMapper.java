package com.vedryxtech.voiceagent.lead.mapper;

import com.vedryxtech.voiceagent.mapper.MapperConfiguration;
import com.vedryxtech.voiceagent.mapper.MapperSupport;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.api.dto.LeadPatchRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadRequest;
import com.vedryxtech.voiceagent.lead.api.dto.LeadResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/** Translates between the wire DTOs and the {@link Lead} document. */
@Mapper(config = MapperConfiguration.class, uses = MapperSupport.class)
public interface LeadMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pipelineStatus", ignore = true)
    @Mapping(target = "finalStatus", ignore = true)
    @Mapping(target = "lastDisposition", ignore = true)
    @Mapping(target = "lastOutcome", ignore = true)
    @Mapping(target = "attemptCount", ignore = true)
    @Mapping(target = "connectedCount", ignore = true)
    @Mapping(target = "lastAttemptAt", ignore = true)
    @Mapping(target = "lastConnectedAt", ignore = true)
    @Mapping(target = "nextAttemptAt", ignore = true)
    @Mapping(target = "totalTalkSeconds", ignore = true)
    @Mapping(target = "lastCallLogId", ignore = true)
    @Mapping(target = "doNotCall", ignore = true)
    @Mapping(target = "lastRecordingUrl", ignore = true)
    @Mapping(target = "version", ignore = true)
    Lead toEntity(LeadRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.SET_TO_NULL)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pipelineStatus", ignore = true)
    @Mapping(target = "finalStatus", ignore = true)
    @Mapping(target = "lastDisposition", ignore = true)
    @Mapping(target = "lastOutcome", ignore = true)
    @Mapping(target = "attemptCount", ignore = true)
    @Mapping(target = "connectedCount", ignore = true)
    @Mapping(target = "lastAttemptAt", ignore = true)
    @Mapping(target = "lastConnectedAt", ignore = true)
    @Mapping(target = "nextAttemptAt", ignore = true)
    @Mapping(target = "totalTalkSeconds", ignore = true)
    @Mapping(target = "lastCallLogId", ignore = true)
    @Mapping(target = "doNotCall", ignore = true)
    @Mapping(target = "lastRecordingUrl", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateClientFields(LeadRequest request, @MappingTarget Lead lead);

    @BeanMapping(ignoreByDefault = false)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "pipelineStatus", ignore = true)
    @Mapping(target = "finalStatus", ignore = true)
    @Mapping(target = "lastDisposition", ignore = true)
    @Mapping(target = "lastOutcome", ignore = true)
    @Mapping(target = "attemptCount", ignore = true)
    @Mapping(target = "connectedCount", ignore = true)
    @Mapping(target = "lastAttemptAt", ignore = true)
    @Mapping(target = "lastConnectedAt", ignore = true)
    @Mapping(target = "nextAttemptAt", ignore = true)
    @Mapping(target = "totalTalkSeconds", ignore = true)
    @Mapping(target = "lastCallLogId", ignore = true)
    @Mapping(target = "doNotCall", source = "doNotCall")
    @Mapping(target = "lastRecordingUrl", ignore = true)
    @Mapping(target = "version", ignore = true)
    void applyPatchValues(LeadPatchRequest patch, @MappingTarget Lead lead);

    @Mapping(target = "id", source = "idAsString")
    @Mapping(target = "lastCallLogId", source = "lastCallLogId", qualifiedByName = "objectIdToString")
    LeadResponse toResponse(Lead lead);

    default void applyFullUpdate(Lead lead, LeadRequest request) {
        updateClientFields(request, lead);
    }

    default void applyPatch(Lead lead, LeadPatchRequest patch) {
        applyPatchValues(patch, lead);
    }
}
