package com.copart.prep.audit;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AuditKind {
    BLOCK,
    RELEASE_BLOCK,
    EXCEPTIONAL_REFUND,
    EVENT_CANCELLATION;

    @JsonValue
    public String wireValue() {
        return switch (this) {
            case BLOCK -> "block";
            case RELEASE_BLOCK -> "release";
            case EXCEPTIONAL_REFUND -> "exceptional-refund";
            case EVENT_CANCELLATION -> "event-cancellation";
        };
    }
}
