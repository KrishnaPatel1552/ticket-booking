ALTER TABLE customer_order
    ADD COLUMN promo_code_id BIGINT REFERENCES promo_code (id);
