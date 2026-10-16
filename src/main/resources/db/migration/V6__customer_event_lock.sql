CREATE TABLE customer_event (
    customer_id BIGINT NOT NULL REFERENCES account (id),
    event_id BIGINT NOT NULL REFERENCES event (id),
    PRIMARY KEY (customer_id, event_id)
);
