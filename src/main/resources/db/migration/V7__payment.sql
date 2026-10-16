CREATE TABLE payment (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES customer_order (id),
    amount_cents INTEGER NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'UNKNOWN', 'SUCCEEDED', 'DECLINED')),
    created_at TIMESTAMPTZ NOT NULL
);
