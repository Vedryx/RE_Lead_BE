package com.vedryxtech.voiceagent.common.crypto;

import com.vedryxtech.voiceagent.call.application.CallOrchestrationService;
import com.vedryxtech.voiceagent.call.application.LeadCallLogService;
import com.vedryxtech.voiceagent.call.domain.LeadCallLog;
import com.vedryxtech.voiceagent.lead.application.LeadAuditService;
import com.vedryxtech.voiceagent.lead.application.LeadSearchCriteria;
import com.vedryxtech.voiceagent.lead.application.LeadService;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadAuditEntry;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import com.vedryxtech.voiceagent.lead.persistence.LeadRepository;
import com.vedryxtech.voiceagent.settings.application.SettingsService;
import com.vedryxtech.voiceagent.settings.domain.AppSettings;
import com.vedryxtech.voiceagent.settings.domain.CallPolicy;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phone numbers against a real Mongo: ciphertext in every stored document, plaintext
 * in every object the service hands out — including the one the dialler rings.
 */
@SpringBootTest
class PhoneEncryptionIntegrationTest {

    private static final String PHONE = "+919812345678";

    @Autowired private LeadRepository leadRepository;
    @Autowired private LeadService leadService;
    @Autowired private LeadCallLogService callLogService;
    @Autowired private LeadAuditService auditService;
    @Autowired private CallOrchestrationService orchestration;
    @Autowired private SettingsService settingsService;
    @Autowired private PhoneCipher cipher;
    @Autowired private MongoTemplate mongoTemplate;

    @BeforeEach
    void freshSlate() {
        mongoTemplate.getCollectionNames().forEach(name -> {
            if (!name.equals("app_user") && !name.equals("app_settings")) {
                mongoTemplate.dropCollection(name);
            }
        });
        AppSettings settings = settingsService.current();
        settings.setCallPolicy(CallPolicy.defaults());
        settingsService.save(settings);
    }

    private Lead saveLead(String phone) {
        Lead lead = new Lead();
        lead.setName("Dev");
        lead.setPhone(phone);
        lead.setCallingPhone(phone);
        lead.setWhatsappPhone(phone);
        lead.setProject("My Home Sanctuary");
        lead.setStage(LeadStage.NEW);
        lead.setPipelineStatus(LeadPipelineStatus.NEW);
        lead.setAttemptCount(0);
        lead.setConnectedCount(0);
        lead.setTotalTalkSeconds(0);
        lead.setDoNotCall(Boolean.FALSE);
        lead.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        lead.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        lead.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return leadRepository.save(lead);
    }

    private Document raw(String collection) {
        return mongoTemplate.getCollection(collection).find().first();
    }

    @Test
    void encryption_is_on_in_this_run() {
        assertThat(cipher.isEnabled()).isTrue();
    }

    @Test
    void a_lead_stores_ciphertext_and_reads_back_plaintext() {
        saveLead(PHONE);

        Document stored = raw("lead");
        for (String field : List.of("phone", "calling_phone", "whatsapp_phone")) {
            assertThat(stored.getString(field)).as(field)
                    .startsWith("enc:v1:")
                    .doesNotContain("9812345678");
        }
        assertThat(stored.toJson()).doesNotContain("9812345678");

        Lead read = leadRepository.findAll().get(0);
        assertThat(read.getPhone()).isEqualTo(PHONE);
        assertThat(read.getCallingPhone()).isEqualTo(PHONE);
        assertThat(read.getWhatsappPhone()).isEqualTo(PHONE);
    }

    @Test
    void derived_queries_match_on_the_plaintext_number() {
        saveLead(PHONE);

        assertThat(leadRepository.findByCallingPhone(PHONE)).isPresent();
        assertThat(leadRepository.existsByCallingPhone(PHONE)).isTrue();
        assertThat(leadRepository.existsByCallingPhone("+919800000000")).isFalse();
    }

    @Test
    void the_unique_index_still_refuses_a_second_lead_for_one_number() {
        // Dropping the collection between tests takes its indexes with it.
        IndexOperations indexes = mongoTemplate.indexOps(Lead.class);
        new MongoPersistentEntityIndexResolver(mongoTemplate.getConverter().getMappingContext())
                .resolveIndexFor(Lead.class).forEach(indexes::createIndex);
        saveLead(PHONE);

        assertThatThrownBy(() -> saveLead(PHONE)).isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void the_phone_search_finds_the_lead_however_the_number_is_typed() {
        saveLead(PHONE);

        LeadSearchCriteria criteria = new LeadSearchCriteria(null, null, null, null, null, null,
                null, "+91 98123 45678", null, null, null, null, null, null, null, null, null);

        assertThat(leadService.search(criteria, PageRequest.of(0, 10)).getContent())
                .extracting(Lead::getCallingPhone)
                .containsExactly(PHONE);
    }

    @Test
    void the_dialler_is_handed_the_plaintext_number_and_the_call_log_stores_ciphertext() {
        saveLead(PHONE);

        List<CallOrchestrationService.CallSession> sessions = orchestration.claimNext(1);

        assertThat(sessions).hasSize(1);
        // OutboundDialScheduler rings exactly this value and puts it in the agent's metadata.
        assertThat(sessions.get(0).lead().getCallingPhone()).isEqualTo(PHONE);
        assertThat(raw("leads_log").getString("phone")).startsWith("enc:v1:");

        LeadCallLog found = callLogService.search(new LeadCallLogService.CallLogSearchCriteria(
                null, PHONE, null, null, null, null, null, null), PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(found.getPhone()).isEqualTo(PHONE);
    }

    @Test
    void the_audit_trail_stores_a_changed_number_encrypted_and_shows_it_decrypted() {
        Lead lead = saveLead(PHONE);
        Map<String, Object> before = auditService.snapshot(lead);
        lead.setCallingPhone("+919811111111");
        Lead saved = leadRepository.save(lead);

        auditService.record(before, saved, "patch");

        assertThat(raw("lead_audit").toJson())
                .doesNotContain("9812345678")
                .doesNotContain("9811111111");
        LeadAuditEntry entry = auditService.history(saved, PageRequest.of(0, 10)).getContent().get(0);
        assertThat(entry.getChanges()).containsExactly(
                new LeadAuditEntry.FieldChange("callingPhone", PHONE, "+919811111111"));
    }

    @Test
    void a_row_not_yet_migrated_still_reads() {
        mongoTemplate.getCollection("lead").insertOne(new Document("name", "Legacy")
                .append("phone", PHONE).append("calling_phone", PHONE));

        Lead read = leadRepository.findAll().get(0);
        assertThat(read.getCallingPhone()).isEqualTo(PHONE);
    }
}
