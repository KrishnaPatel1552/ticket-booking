ALTER TABLE refund
    ADD COLUMN kind VARCHAR(32) NOT NULL DEFAULT 'CUSTOMER_CANCEL';

ALTER TABLE refund
    ADD CONSTRAINT refund_kind_check CHECK (kind IN ('CUSTOMER_CANCEL', 'EXCEPTIONAL'));

ALTER TABLE audit_entry DROP CONSTRAINT audit_entry_kind_check;

ALTER TABLE audit_entry
    ADD CONSTRAINT audit_entry_kind_check
    CHECK (kind IN ('BLOCK', 'RELEASE_BLOCK', 'EXCEPTIONAL_REFUND'));
