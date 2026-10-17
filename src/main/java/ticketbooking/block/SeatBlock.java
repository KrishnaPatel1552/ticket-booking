package ticketbooking.block;

import ticketbooking.event.Event;
import ticketbooking.venue.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "seat_block")
public class SeatBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Event event;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Seat seat;

    @Column(nullable = false)
    private Instant blockedAt;

    @Column(nullable = false)
    private boolean active;

    protected SeatBlock() {
    }

    public SeatBlock(Event event, Seat seat, Instant blockedAt) {
        this.event = event;
        this.seat = seat;
        this.blockedAt = blockedAt;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public Instant getBlockedAt() {
        return blockedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void release() {
        this.active = false;
    }
}
