CREATE TABLE venue (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    time_zone VARCHAR(64) NOT NULL
);

CREATE TABLE event (
    id BIGSERIAL PRIMARY KEY,
    venue_id BIGINT NOT NULL REFERENCES venue (id),
    name VARCHAR(255) NOT NULL,
    starts_at TIMESTAMPTZ NOT NULL,
    time_zone VARCHAR(64) NOT NULL,
    early_bird_starts_at TIMESTAMPTZ,
    early_bird_ends_at TIMESTAMPTZ
);
