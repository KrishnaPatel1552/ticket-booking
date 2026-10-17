CREATE TABLE price_tier (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venue (id),
    name VARCHAR(255) NOT NULL,
    price_cents INTEGER NOT NULL,
    early_bird_price_cents INTEGER
);

CREATE TABLE section (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venue (id),
    name VARCHAR(255) NOT NULL
);

CREATE TABLE seat (
    id BIGSERIAL PRIMARY KEY,
    section_id BIGINT NOT NULL REFERENCES section (id),
    price_tier_id BIGINT NOT NULL REFERENCES price_tier (id),
    label VARCHAR(32) NOT NULL,
    UNIQUE (section_id, label)
);
