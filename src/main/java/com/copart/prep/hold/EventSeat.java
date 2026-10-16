package com.copart.prep.hold;

import com.copart.prep.event.Event;
import com.copart.prep.venue.Seat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "event_seat")
public class EventSeat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Event event;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventSeatKind kind;

    private Instant heldUntil;

    @Column(nullable = false)
    private boolean active;

    protected EventSeat() {
    }

    public EventSeat(CustomerOrder order, Event event, Seat seat, EventSeatKind kind, Instant heldUntil) {
        this.order = order;
        this.event = event;
        this.seat = seat;
        this.kind = kind;
        this.heldUntil = heldUntil;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public EventSeatKind getKind() {
        return kind;
    }

    public Instant getHeldUntil() {
        return heldUntil;
    }

    public boolean isActive() {
        return active;
    }
}
