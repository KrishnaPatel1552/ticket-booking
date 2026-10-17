package com.copart.prep.checkout;

import com.copart.prep.audit.AuditEntry;
import com.copart.prep.audit.AuditEntryRepository;
import com.copart.prep.audit.AuditKind;
import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.refund.RefundRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordEventCancellation {

    private final AccountRepository accounts;
    private final EventRepository events;
    private final AuditEntryRepository auditEntries;
    private final RefundRepository refunds;
    private final AppClock clock;

    public RecordEventCancellation(
            AccountRepository accounts,
            EventRepository events,
            AuditEntryRepository auditEntries,
            RefundRepository refunds,
            AppClock clock) {
        this.accounts = accounts;
        this.events = events;
        this.auditEntries = auditEntries;
        this.refunds = refunds;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Long> record(long eventId, String reason, String adminEmail) {
        Account admin = accounts.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!event.isCancelled()) {
            Instant now = clock.now();
            event.cancel(now);
            auditEntries.save(new AuditEntry(
                    admin, event, null, AuditKind.EVENT_CANCELLATION, reason, now));
        }
        return refunds.findOpenCustomerCancels(eventId).stream().map(refund -> refund.getId()).toList();
    }
}
