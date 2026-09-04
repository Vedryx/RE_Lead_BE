package com.vedryxtech.voiceagent.bootstrap;

import com.vedryxtech.voiceagent.lead.domain.ActionType;
import com.vedryxtech.voiceagent.lead.domain.Lead;
import com.vedryxtech.voiceagent.lead.domain.LeadFinalStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadPipelineStatus;
import com.vedryxtech.voiceagent.lead.domain.LeadStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives every pre-existing lead a stage, once.
 *
 * <p>The dialler's claim filters on {@code stage in (new, followUp)}. A lead written
 * before the field existed has no stage, matches nothing, and silently stops being
 * called — so this is not cosmetic tidying, it is the difference between an existing
 * lead being dialled and being lost.
 *
 * <p>Idempotent: it only touches documents with no stage, so restarting is free and it
 * never overwrites a stage a human or an outcome has since set.
 */
@Component
public class LeadStageBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LeadStageBackfill.class);

    private final MongoTemplate mongoTemplate;

    public LeadStageBackfill(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Lead> unstaged = mongoTemplate.find(
                Query.query(new Criteria().orOperator(
                        Criteria.where("stage").exists(false),
                        Criteria.where("stage").is(null))),
                Lead.class);

        if (unstaged.isEmpty()) {
            return;
        }
        for (Lead lead : unstaged) {
            lead.setStage(inferStage(lead));
            mongoTemplate.save(lead);
        }
        log.info("Backfilled a stage onto {} lead(s) written before the field existed",
                unstaged.size());
    }

    /**
     * Reconstruct the funnel position from what the lead already records. Coarse on
     * purpose — the precise reason stays in finalStatus.
     */
    static LeadStage inferStage(Lead lead) {
        LeadFinalStatus finalStatus = lead.getFinalStatus();
        if (finalStatus != null) {
            return switch (finalStatus) {
                case SITE_VISIT_BOOKED, SITE_VISIT_DONE -> LeadStage.SITE_VISIT;
                case INTERESTED -> LeadStage.FOLLOW_UP;
                case STAY_IN_TOUCH -> LeadStage.NURTURE;
                case NOT_INTERESTED, UNQUALIFIED, UNREACHABLE, NO_DECISION,
                     DO_NOT_CALL, WRONG_NUMBER, DUPLICATE -> LeadStage.DISCARDED;
            };
        }
        if (Boolean.TRUE.equals(lead.getDoNotCall())
                || lead.getPipelineStatus() == LeadPipelineStatus.SUPPRESSED
                || lead.getPipelineStatus() == LeadPipelineStatus.EXHAUSTED) {
            return LeadStage.DISCARDED;
        }
        ActionType actionType = lead.getActionType();
        if (actionType != null) {
            return switch (actionType) {
                case SITE_VISIT -> LeadStage.SITE_VISIT;
                case MEETING -> LeadStage.MEETING;
                case TEAM_CALLBACK -> LeadStage.CALLBACK_REQUESTED;
                case FOLLOW_UP_CALL, WHATSAPP_PROJECT_DETAILS -> LeadStage.FOLLOW_UP;
            };
        }
        return LeadStage.NEW;
    }
}
