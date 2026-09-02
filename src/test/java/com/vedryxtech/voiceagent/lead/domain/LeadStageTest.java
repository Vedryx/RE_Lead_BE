package com.vedryxtech.voiceagent.lead.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ratchet, exercised without a database.
 *
 * <p>These are cheap and they guard the one failure that would be invisible in
 * production: a lead quietly sliding backwards down the funnel, so a booked visit
 * stops being counted as won.
 */
class LeadStageTest {

    @Test
    @DisplayName("forward moves apply")
    void forwardMovesApply() {
        assertThat(LeadStage.NEW.advanceTo(LeadStage.FOLLOW_UP)).isEqualTo(LeadStage.FOLLOW_UP);
        assertThat(LeadStage.FOLLOW_UP.advanceTo(LeadStage.CALLBACK_REQUESTED))
                .isEqualTo(LeadStage.CALLBACK_REQUESTED);
        assertThat(LeadStage.CALLBACK_REQUESTED.advanceTo(LeadStage.SITE_VISIT))
                .isEqualTo(LeadStage.SITE_VISIT);
    }

    @Test
    @DisplayName("a booked visit is never dragged back by a later call")
    void backwardMovesAreIgnored() {
        // The regression this exists for: a missed reminder call on a booked visit must
        // not return the lead to NEW and un-count the win.
        assertThat(LeadStage.SITE_VISIT.advanceTo(LeadStage.NEW)).isEqualTo(LeadStage.SITE_VISIT);
        assertThat(LeadStage.SITE_VISIT.advanceTo(LeadStage.FOLLOW_UP)).isEqualTo(LeadStage.SITE_VISIT);
        assertThat(LeadStage.CALLBACK_REQUESTED.advanceTo(LeadStage.FOLLOW_UP))
                .isEqualTo(LeadStage.CALLBACK_REQUESTED);
    }

    @Test
    @DisplayName("staying put is a no-op")
    void sameStageIsStable() {
        for (LeadStage stage : LeadStage.values()) {
            assertThat(stage.advanceTo(stage)).isEqualTo(stage);
        }
    }

    @ParameterizedTest
    @EnumSource(LeadStage.class)
    @DisplayName("an exit applies from anywhere, including from a terminal stage")
    void discardAlwaysWins(LeadStage from) {
        assertThat(from.advanceTo(LeadStage.DISCARDED)).isEqualTo(LeadStage.DISCARDED);
    }

    @ParameterizedTest
    @EnumSource(LeadStage.class)
    @DisplayName("discarded stays discarded, whatever a later call reports")
    void terminalIsFinalForAgentTransitions(LeadStage next) {
        LeadStage result = LeadStage.DISCARDED.advanceTo(next);
        assertThat(result).isEqualTo(LeadStage.DISCARDED);
    }

    @Test
    @DisplayName("a null transition changes nothing")
    void nullIsANoOp() {
        assertThat(LeadStage.FOLLOW_UP.advanceTo(null)).isEqualTo(LeadStage.FOLLOW_UP);
    }

    @Test
    @DisplayName("only NEW and FOLLOW_UP may be dialled by the agent")
    void agentCallableStages() {
        assertThat(LeadStage.NEW.isAgentCallable()).isTrue();
        assertThat(LeadStage.FOLLOW_UP.isAgentCallable()).isTrue();
        // A person owns these; an agent dialling in behind them contradicts a colleague.
        assertThat(LeadStage.CALLBACK_REQUESTED.isAgentCallable()).isFalse();
        assertThat(LeadStage.SITE_VISIT.isAgentCallable()).isFalse();
        assertThat(LeadStage.DISCARDED.isAgentCallable()).isFalse();
    }

    @Test
    @DisplayName("wire values are camelCase and round-trip")
    void wireValuesRoundTrip() {
        for (LeadStage stage : LeadStage.values()) {
            assertThat(LeadStage.fromValue(stage.getValue())).isEqualTo(stage);
        }
        assertThat(LeadStage.fromValue("followUp")).isEqualTo(LeadStage.FOLLOW_UP);
        assertThat(LeadStage.fromValue("FOLLOW_UP")).isEqualTo(LeadStage.FOLLOW_UP);
    }

    @Test
    @DisplayName("exactly one stage is terminal")
    void onlyDiscardedIsTerminal() {
        assertThat(java.util.Arrays.stream(LeadStage.values()).filter(LeadStage::isTerminal).toList())
                .containsExactly(LeadStage.DISCARDED);
    }
}
