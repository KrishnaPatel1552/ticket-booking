ALTER TABLE event
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE waitlist_entry
    ADD COLUMN ended_at TIMESTAMPTZ;

ALTER TABLE refund DROP CONSTRAINT refund_kind_check;

ALTER TABLE refund
    ADD CONSTRAINT refund_kind_check
    CHECK (kind IN ('CUSTOMER_CANCEL', 'EXCEPTIONAL', 'EVENT_CANCELLATION'));

ALTER TABLE audit_entry DROP CONSTRAINT audit_entry_kind_check;

ALTER TABLE audit_entry
    ADD CONSTRAINT audit_entry_kind_check
    CHECK (kind IN ('BLOCK', 'RELEASE_BLOCK', 'EXCEPTIONAL_REFUND', 'EVENT_CANCELLATION'));
