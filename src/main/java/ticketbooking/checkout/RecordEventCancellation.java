package ticketbooking.checkout;

import ticketbooking.audit.AuditEntry;
import ticketbooking.audit.AuditEntryRepository;
import ticketbooking.audit.AuditKind;
import ticketbooking.clock.AppClock;
import ticketbooking.event.Event;
import ticketbooking.event.EventRepository;
import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.refund.RefundRepository;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
import ticketbooking.waitlist.WaitlistEntry;
import ticketbooking.waitlist.WaitlistEntryRepository;
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
    private final WaitlistEntryRepository waitlistEntries;
    private final EventSeatRepository eventSeats;
    private final AppClock clock;

    public RecordEventCancellation(
            AccountRepository accounts,
            EventRepository events,
            AuditEntryRepository auditEntries,
            RefundRepository refunds,
            WaitlistEntryRepository waitlistEntries,
            EventSeatRepository eventSeats,
            AppClock clock) {
        this.accounts = accounts;
        this.events = events;
        this.auditEntries = auditEntries;
        this.refunds = refunds;
        this.waitlistEntries = waitlistEntries;
        this.eventSeats = eventSeats;
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
            for (WaitlistEntry entry : waitlistEntries.findByEventIdAndEndedAtIsNullOrderByIdAsc(eventId)) {
                entry.end(now);
            }
            for (EventSeat hold : eventSeats.findActiveWaitlistHoldsForEvent(eventId)) {
                hold.release();
            }
        }
        return refunds.findOpenCustomerCancels(eventId).stream().map(refund -> refund.getId()).toList();
    }
}
