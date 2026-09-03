package com.vedryxtech.voiceagent.whatsapp;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.storage.ProjectMediaService;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sending is best-effort by design. A lead who asked for a brochure has already been
 * moved to follow-up by the time this runs, and a Meta outage must not undo that —
 * the lead stays flagged as owed a message for a person to send by hand.
 */
class WhatsAppNotificationServiceTest {

    private WhatsAppClient client;
    private ProjectMediaService media;
    private WhatsAppNotificationService service;

    @BeforeEach
    void setUp() {
        client = mock(WhatsAppClient.class);
        media = mock(ProjectMediaService.class);
        service = new WhatsAppNotificationService(Optional.of(client), media);
    }

    @Test
    void a_brochure_and_photos_are_sent_to_the_lead() {
        when(media.mediaFor("My Home Sanctuary")).thenReturn(
                new ProjectMediaService.ProjectMedia(List.of("project-details/mhs/brochure.pdf"),
                        List.of("project-details/mhs/tower.jpg")));
        when(media.presignedGet(anyString())).thenReturn("https://signed");

        service.sendProjectDetails(lead("+919876543210"));

        verify(client).sendDocument(anyString(), anyString(), anyString());
        verify(client).sendImage(anyString(), anyString());
    }

    @Test
    void a_failure_at_meta_never_reaches_the_caller() {
        // The lead has already been parked on a follow-up by the time this runs.
        // Throwing here would undo a decision that was correct.
        when(media.mediaFor(anyString())).thenReturn(
                new ProjectMediaService.ProjectMedia(List.of("a.pdf"), List.of()));
        when(media.presignedGet(anyString())).thenReturn("https://signed");
        org.mockito.Mockito.doThrow(new RuntimeException("429 from Meta"))
                .when(client).sendDocument(anyString(), anyString(), anyString());

        assertThatCode(() -> service.sendProjectDetails(lead("+919876543210")))
                .doesNotThrowAnyException();
    }

    @Test
    void nothing_is_sent_when_the_project_has_no_media() {
        when(media.mediaFor(anyString())).thenReturn(
                new ProjectMediaService.ProjectMedia(List.of(), List.of()));

        service.sendProjectDetails(lead("+919876543210"));

        verify(client, never()).sendDocument(anyString(), anyString(), anyString());
        verify(client, never()).sendImage(anyString(), anyString());
    }

    @Test
    void a_lead_with_no_number_is_skipped_rather_than_guessed_at() {
        service.sendProjectDetails(lead(null));

        verify(client, never()).sendDocument(anyString(), anyString(), anyString());
    }

    @Test
    void with_whatsapp_unconfigured_the_lead_is_left_for_a_person() {
        // A supported state: the lead stays flagged as wanting details, and somebody
        // sends them by hand. Not an error.
        var unconfigured = new WhatsAppNotificationService(Optional.empty(), media);

        assertThatCode(() -> unconfigured.sendProjectDetails(lead("+919876543210")))
                .doesNotThrowAnyException();
    }

    private static Lead lead(String whatsappPhone) {
        Lead lead = new Lead();
        lead.setId(new ObjectId());
        lead.setProject("My Home Sanctuary");
        lead.setWhatsappPhone(whatsappPhone);
        return lead;
    }
}
