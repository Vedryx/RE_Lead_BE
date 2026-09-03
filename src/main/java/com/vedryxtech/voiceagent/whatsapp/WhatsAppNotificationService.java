package com.vedryxtech.voiceagent.whatsapp;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.storage.ProjectMediaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Sends a lead the project's brochure and photos on WhatsApp once they've asked for them.
 *
 * <p>Called from {@code CallOrchestrationServiceImpl} the moment a call ends in disposition
 * {@code detailsRequested}. Never throws: a failed send must not fail the outcome that
 * triggered it, the same rule {@code CallArtifactService} follows for the recording archive.
 * With WhatsApp not configured, or nothing in the project's folder, this quietly does
 * nothing — the lead stays flagged {@code whatsappProjectDetails} / {@code requested} for a
 * person to notice and follow up by hand.
 */
@Service
public class WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    private final Optional<WhatsAppClient> client;
    private final ProjectMediaService projectMediaService;

    public WhatsAppNotificationService(Optional<WhatsAppClient> client, ProjectMediaService projectMediaService) {
        this.client = client;
        this.projectMediaService = projectMediaService;
    }

    public boolean isEnabled() {
        return client.isPresent();
    }

    public void sendProjectDetails(Lead lead) {
        if (client.isEmpty()) {
            log.debug("WhatsApp is not configured; leaving lead {} as requested for a person to send.",
                    lead.getIdAsString());
            return;
        }
        String toPhone = toWhatsAppFormat(lead.getWhatsappPhone());
        if (toPhone == null) {
            log.warn("Lead {} asked for details but has no WhatsApp-able number.", lead.getIdAsString());
            return;
        }

        var media = projectMediaService.mediaFor(lead.getProject());
        if (media.isEmpty()) {
            log.warn("Lead {} asked for details on project '{}' but its media folder is empty.",
                    lead.getIdAsString(), lead.getProject());
            return;
        }

        try {
            sendWhatIsThere(client.get(), toPhone, media);
        } catch (RuntimeException ex) {
            // Logged and swallowed on purpose — see the class comment. The lead stays
            // flagged as owed a message rather than silently marked done.
            log.error("Could not send project details on WhatsApp to lead {}: {}",
                    lead.getIdAsString(), ex.getMessage());
        }
    }

    private void sendWhatIsThere(WhatsAppClient whatsApp, String toPhone, ProjectMediaService.ProjectMedia media) {
        List<String> documents = media.documentKeys();
        if (!documents.isEmpty()) {
            String key = documents.get(0);
            whatsApp.sendDocument(toPhone, projectMediaService.presignedGet(key), filenameOf(key));
        }
        // WhatsApp carries exactly one attachment per message, so each photo goes out on
        // its own. A partial failure here (say photo 3 of 5) still leaves the earlier ones
        // delivered, which is why this loops rather than batching.
        for (String key : media.photoKeys()) {
            whatsApp.sendImage(toPhone, projectMediaService.presignedGet(key));
        }
    }

    private static String filenameOf(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1);
    }

    /** Meta wants digits only, country code included, no leading {@code +}. */
    private static String toWhatsAppFormat(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("[^0-9]", "");
        return digits.isBlank() ? null : digits;
    }
}
