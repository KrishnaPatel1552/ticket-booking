package com.copart.prep.checkout;

import com.copart.prep.audit.AuditEntry;
import com.copart.prep.audit.AuditEntryRepository;
import com.copart.prep.audit.AuditKind;
import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordReschedule {

    private final AccountRepository accounts;
    private final EventRepository events;
    private final AuditEntryRepository auditEntries;
    private final AppClock clock;

    public RecordReschedule(
            AccountRepository accounts,
            EventRepository events,
            AuditEntryRepository auditEntries,
            AppClock clock) {
        this.accounts = accounts;
        this.events = events;
        this.auditEntries = auditEntries;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void record(long eventId, Instant startsAt, String reason, String adminEmail) {
        Account admin = accounts.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (event.isCancelled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        Instant now = clock.now();
        event.reschedule(startsAt, now);
        auditEntries.save(new AuditEntry(admin, event, null, AuditKind.RESCHEDULE, reason, now));
    }
}
