ALTER TABLE event_seat
    ADD COLUMN starting_price_cents INTEGER,
    ADD COLUMN group_share_cents INTEGER,
    ADD COLUMN promo_share_cents INTEGER,
    ADD COLUMN fee_share_cents INTEGER;
