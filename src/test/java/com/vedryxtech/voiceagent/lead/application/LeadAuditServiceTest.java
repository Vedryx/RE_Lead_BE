package com.vedryxtech.voiceagent.lead.application;

import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadAuditEntry;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.persistence.LeadAuditRepository;
import com.vedryxtech.voiceagent.security.CurrentActor;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** "Who moved this lead" was unanswerable before this. */
class LeadAuditServiceTest {

    private LeadAuditRepository repository;
    private LeadAuditService service;
    private final List<LeadAuditEntry> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        repository = mock(LeadAuditRepository.class);
        when(repository.save(any(LeadAuditEntry.class))).thenAnswer(call -> {
            saved.add(call.getArgument(0));
            return call.getArgument(0);
        });
        CurrentActor actor = mock(CurrentActor.class);
        when(actor.actor()).thenReturn("user-42");
        when(actor.email()).thenReturn(java.util.Optional.of("priya@example.com"));
        service = new LeadAuditService(repository, actor);
    }

    @Test
    void a_stage_change_is_recorded_with_who_did_it() {
        Lead lead = lead();
        Map<String, Object> before = service.snapshot(lead);
        lead.setStage(LeadStage.SITE_VISIT);

        service.record(before, lead, "patch");

        assertThat(saved).hasSize(1);
        LeadAuditEntry entry = saved.get(0);
        assertThat(entry.getActor()).isEqualTo("user-42");
        assertThat(entry.getActorEmail())
                .as("the trail should read without a join against the user collection")
                .isEqualTo("priya@example.com");
        assertThat(entry.getVia()).isEqualTo("patch");
        assertThat(entry.getChanges())
                .containsExactly(new LeadAuditEntry.FieldChange("stage", "NEW", "SITE_VISIT"));
    }

    @Test
    void several_fields_changing_at_once_are_one_entry() {
        Lead lead = lead();
        Map<String, Object> before = service.snapshot(lead);
        lead.setAssignedTo("priya");
        lead.setDoNotCall(Boolean.TRUE);

        service.record(before, lead, "patch");

        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getChanges())
                .extracting(LeadAuditEntry.FieldChange::field)
                .containsExactlyInAnyOrder("assignedTo", "doNotCall");
    }

    @Test
    void a_write_that_changed_nothing_writes_nothing() {
        Lead lead = lead();

        service.record(service.snapshot(lead), lead, "patch");

        assertThat(saved).isEmpty();
    }

    @Test
    void the_counters_the_dialler_moves_are_not_audited() {
        // These change several times a day on their own. Recording them would bury
        // the handful of entries anyone wants to read.
        Lead lead = lead();
        Map<String, Object> before = service.snapshot(lead);
        lead.setAttemptCount(3);
        lead.setTotalTalkSeconds(240);

        service.record(before, lead, "patch");

        assertThat(saved).isEmpty();
    }

    @Test
    void a_failing_audit_write_does_not_fail_the_edit_that_caused_it() {
        Lead lead = lead();
        Map<String, Object> before = service.snapshot(lead);
        lead.setAssignedTo("priya");
        when(repository.save(any(LeadAuditEntry.class)))
                .thenThrow(new IllegalStateException("mongo is down"));

        assertThatCode(() -> service.record(before, lead, "patch")).doesNotThrowAnyException();
    }

    private static Lead lead() {
        Lead lead = new Lead();
        lead.setId(new ObjectId());
        lead.setName("Dev");
        lead.setStage(LeadStage.NEW);
        lead.setPipelineStatus(LeadPipelineStatus.QUEUED);
        lead.setDoNotCall(Boolean.FALSE);
        return lead;
    }

}
