package com.copart.prep.audit;

import com.copart.prep.event.Event;
import com.copart.prep.signin.Account;
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
@Table(name = "audit_entry")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_id")
    private Account admin;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    private Seat seat;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditKind kind;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private Instant recordedAt;

    protected AuditEntry() {
    }

    public AuditEntry(Account admin, Event event, Seat seat, AuditKind kind, String reason, Instant recordedAt) {
        this.admin = admin;
        this.event = event;
        this.seat = seat;
        this.kind = kind;
        this.reason = reason;
        this.recordedAt = recordedAt;
    }

    public Long getId() {
        return id;
    }

    public Account getAdmin() {
        return admin;
    }

    public Event getEvent() {
        return event;
    }

    public Seat getSeat() {
        return seat;
    }

    public AuditKind getKind() {
        return kind;
    }

    public String getReason() {
        return reason;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }
}
