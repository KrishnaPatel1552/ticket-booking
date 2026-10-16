CREATE TABLE seat_block (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL REFERENCES event (id),
    seat_id BIGINT NOT NULL REFERENCES seat (id),
    blocked_at TIMESTAMPTZ NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX one_active_block_per_event_seat
    ON seat_block (event_id, seat_id)
    WHERE active;

CREATE TABLE audit_entry (
    id BIGSERIAL PRIMARY KEY,
    admin_id BIGINT NOT NULL REFERENCES account (id),
    event_id BIGINT NOT NULL REFERENCES event (id),
    seat_id BIGINT REFERENCES seat (id),
    kind VARCHAR(32) NOT NULL CHECK (kind IN ('BLOCK', 'RELEASE_BLOCK')),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) > 0),
    recorded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX audit_entry_by_event
    ON audit_entry (event_id, id);
