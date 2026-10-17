ALTER TABLE event
    ADD COLUMN rescheduled_at TIMESTAMPTZ;

ALTER TABLE audit_entry DROP CONSTRAINT audit_entry_kind_check;

ALTER TABLE audit_entry
    ADD CONSTRAINT audit_entry_kind_check
    CHECK (kind IN ('BLOCK', 'RELEASE_BLOCK', 'EXCEPTIONAL_REFUND', 'EVENT_CANCELLATION', 'RESCHEDULE'));
