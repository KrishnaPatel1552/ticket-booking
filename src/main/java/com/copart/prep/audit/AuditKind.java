package com.copart.prep.audit;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AuditKind {
    BLOCK,
    RELEASE_BLOCK;

    @JsonValue
    public String wireValue() {
        return switch (this) {
            case BLOCK -> "block";
            case RELEASE_BLOCK -> "release";
        };
    }
}
