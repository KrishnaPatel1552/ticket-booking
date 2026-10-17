package ticketbooking.waitlist;

import ticketbooking.event.Event;
import ticketbooking.signin.Account;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

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

    private Instant endedAt;

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

    public Instant getEndedAt() {
        return endedAt;
    }

    public void end(Instant at) {
        this.endedAt = at;
    }
}
