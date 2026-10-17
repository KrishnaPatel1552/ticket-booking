CREATE TABLE sale_terms (
    id BIGSERIAL PRIMARY KEY,
    group_rate_threshold INTEGER NOT NULL,
    group_rate_amount_cents INTEGER NOT NULL,
    service_fee_cents INTEGER NOT NULL
);
