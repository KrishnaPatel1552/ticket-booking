package com.copart.prep.waitlist;

import com.copart.prep.event.Event;
import com.copart.prep.signin.Account;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "waitlist_entry")
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Event event;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Account customer;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(Event event, Account customer) {
        this.event = event;
        this.customer = customer;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Account getCustomer() {
        return customer;
    }
}
