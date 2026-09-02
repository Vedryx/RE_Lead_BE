package com.vedryxtech.voiceagent.call.domain;

import org.springframework.data.mongodb.core.mapping.Field;

/**
 * One thing somebody said on a call.
 *
 * <p>Embedded on the call log rather than kept only in object storage. The archive
 * copy in R2 sits beside the audio and survives the database; this copy is the one
 * that answers "which leads asked about Wakad", which a JSON blob behind a signed
 * URL cannot.
 *
 * <p>{@code atSeconds} is an offset from the first turn, not a wall clock. The agent
 * measures with a monotonic clock, which means nothing outside the process that read
 * it, so it is converted before it is sent.
 */
public class TranscriptTurn {

    /** {@code agent} or {@code lead}. */
    @Field("role")
    private String role;

    @Field("text")
    private String text;

    @Field("at_seconds")
    private Integer atSeconds;

    public TranscriptTurn() {
    }

    public TranscriptTurn(String role, String text, Integer atSeconds) {
        this.role = role;
        this.text = text;
        this.atSeconds = atSeconds;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public Integer getAtSeconds() {
        return atSeconds;
    }

    public void setAtSeconds(Integer atSeconds) {
        this.atSeconds = atSeconds;
    }
}
