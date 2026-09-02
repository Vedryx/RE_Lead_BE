package com.vedryxtech.voiceagent.lead.api.dto;

import com.vedryxtech.voiceagent.lead.domain.LeadAuditEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/** One manual edit to a lead, as the dashboard shows it. */
@Schema(description = "Who changed what on this lead, and when")
public record LeadAuditResponse(
        String id,
        OffsetDateTime at,
        @Schema(description = "User id from the access token, or 'ai_agent'") String actor,
        @Schema(description = "The same actor's email, when the token carried one") String actorEmail,
        @Schema(example = "patch") String via,
        List<LeadAuditEntry.FieldChange> changes
) {
    public static LeadAuditResponse from(LeadAuditEntry entry) {
        return new LeadAuditResponse(entry.getIdAsString(), entry.getAt(), entry.getActor(),
                entry.getActorEmail(), entry.getVia(), entry.getChanges());
    }
}
