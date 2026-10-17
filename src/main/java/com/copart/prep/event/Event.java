package com.copart.prep.event;

import com.copart.prep.venue.Venue;
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
@Table(name = "event")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Instant startsAt;

    @Column(nullable = false)
    private String timeZone;

    private Instant earlyBirdStartsAt;

    private Instant earlyBirdEndsAt;

    private Instant cancelledAt;

    private Instant rescheduledAt;

    protected Event() {
    }

    public Event(
            Venue venue,
            String name,
            Instant startsAt,
            String timeZone,
            Instant earlyBirdStartsAt,
            Instant earlyBirdEndsAt) {
        this.venue = venue;
        this.name = name;
        this.startsAt = startsAt;
        this.timeZone = timeZone;
        this.earlyBirdStartsAt = earlyBirdStartsAt;
        this.earlyBirdEndsAt = earlyBirdEndsAt;
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getName() {
        return name;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Instant getEarlyBirdStartsAt() {
        return earlyBirdStartsAt;
    }

    public Instant getEarlyBirdEndsAt() {
        return earlyBirdEndsAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public boolean isCancelled() {
        return cancelledAt != null;
    }

    public void cancel(Instant at) {
        this.cancelledAt = at;
    }

    public Instant getRescheduledAt() {
        return rescheduledAt;
    }

    public void reschedule(Instant startsAt, Instant at) {
        this.startsAt = startsAt;
        this.rescheduledAt = at;
    }
}
