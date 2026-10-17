CREATE TABLE promo_code (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    discount_cents INTEGER NOT NULL,
    global_limit INTEGER NOT NULL,
    per_customer_limit INTEGER NOT NULL,
    exclusive BOOLEAN NOT NULL
);
