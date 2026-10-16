CREATE TABLE customer_order (
    id BIGSERIAL PRIMARY KEY,
    customer_id BIGINT NOT NULL REFERENCES account (id),
    event_id BIGINT NOT NULL REFERENCES event (id),
    opened_at TIMESTAMPTZ NOT NULL,
    open_for_holds BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX one_open_order_per_customer_event
    ON customer_order (customer_id, event_id)
    WHERE open_for_holds;

CREATE TABLE event_seat (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES customer_order (id),
    event_id BIGINT NOT NULL REFERENCES event (id),
    seat_id BIGINT NOT NULL REFERENCES seat (id),
    kind VARCHAR(16) NOT NULL CHECK (kind IN ('HOLD', 'TICKET')),
    held_until TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE UNIQUE INDEX one_active_event_seat
    ON event_seat (event_id, seat_id)
    WHERE active;
