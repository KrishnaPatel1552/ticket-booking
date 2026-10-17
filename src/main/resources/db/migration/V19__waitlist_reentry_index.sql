drop index one_waitlist_entry_per_customer_event;

create unique index one_waitlist_entry_per_customer_event
    on waitlist_entry (event_id, customer_id)
    where ended_at is null;
