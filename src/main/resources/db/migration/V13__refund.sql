CREATE TABLE refund (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL REFERENCES customer_order (id),
    amount_cents INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX refund_order_id ON refund (order_id);

CREATE TABLE refund_ticket (
    refund_id BIGINT NOT NULL REFERENCES refund (id),
    event_seat_id BIGINT NOT NULL REFERENCES event_seat (id),
    PRIMARY KEY (refund_id, event_seat_id)
);
