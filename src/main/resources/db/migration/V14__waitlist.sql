CREATE TABLE waitlist_entry (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES event (id),
    customer_id BIGINT NOT NULL REFERENCES account (id)
);

CREATE UNIQUE INDEX one_waitlist_entry_per_customer_event
    ON waitlist_entry (event_id, customer_id);

ALTER TABLE event_seat
    ADD COLUMN from_waitlist BOOLEAN NOT NULL DEFAULT FALSE;
