package com.vedryxtech.voiceagent.call.api.dto;

import com.vedryxtech.voiceagent.call.domain.CallDisposition;
import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.call.domain.CallOutcome;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.time.OffsetDateTime;

/**
 * Posted when the leg hangs up. This single call closes the attempt and moves the lead:
 * retry, callback, or closed with a final status.
 *
 * <p>It is also where a fresh lead gets filled in. A lead is created with just a name and a
 * number; the {@code lead_*} fields below let the agent write back what it learned on the
 * call, so after the first conversation the lead record is complete.</p>
 */
public record CallOutcomeRequest(

        @NotNull(message = "outcome is required")
        @Schema(example = "answered", description = "Did the phone connect? answered, noAnswer, busy, rejected, voicemail, invalidNumber, failed")
        CallOutcome outcome,

        @Schema(example = "siteVisitBooked", description = "What the person said. Required when outcome is answered.")
        CallDisposition disposition,

        @Schema(example = "followUpCall",
                description = "Which action was agreed. Distinguishes a callback the agent "
                        + "will make itself (followUpCall) from one handed to a person "
                        + "(teamCallback) — both arrive as disposition callbackRequested, "
                        + "and without this the backend has to guess. Optional: omitted, a "
                        + "callback is treated as teamCallback, as it always has been.")
        ActionType actionType,

        @PositiveOrZero
        @Schema(example = "18")
        Integer ringSeconds,

        @PositiveOrZero
        @Schema(example = "214", description = "Seconds of actual conversation. 0 if nobody picked up.")
        Integer talkSeconds,

        @Size(max = 2000)
        @Schema(example = "Wants the 3 BHK, visiting Saturday.")
        String summary,

        @Size(max = 2000)
        String notes,

        @Schema(example = "2026-09-02T18:30:00+05:30", description = "Required when disposition is callbackRequested or rescheduled")
        OffsetDateTime requestedCallbackAt,

        @Schema(example = "2026-09-06T11:00:00+05:30", description = "Required when disposition is siteVisitBooked")
        OffsetDateTime siteVisitAt,

        @Size(max = 1000)
        String recordingUrl,

        // --- what the call taught us about the lead itself ---

        @Size(max = 150)
        @Schema(description = "Corrects the lead's name if the agent learned the real one")
        String leadName,

        @Size(max = 200)
        @Schema(description = "The project the lead is actually interested in",
                example = "My Home Sanctuary")
        String leadProject,

        @Size(max = 2000)
        @Schema(description = "What the lead asked about",
                example = "Can 2 BHK and 3 BHK be combined to make 5 BHK?")
        String leadQuery,

        @Size(max = 25)
        @Schema(description = "WhatsApp number, when it differs from the number we called")
        String whatsappPhone,

        @Size(max = 64)
        String errorCode,

        @Size(max = 1000)
        String errorMessage,

        // --- what was actually said ---

        @Valid
        @Size(max = 2000, message = "a call with more than 2000 turns is a malfunction")
        @Schema(description = "The conversation, oldest first. Redacted by the agent before "
                + "it is sent: card numbers, account numbers and one-time codes never leave "
                + "the call.")
        List<TranscriptTurnRequest> transcript,

        @Min(0)
        @Schema(description = "Turns before truncation. Larger than transcript.size() means "
                + "the stored copy is trimmed.", example = "31")
        Integer transcriptTurnCount
) {
}
