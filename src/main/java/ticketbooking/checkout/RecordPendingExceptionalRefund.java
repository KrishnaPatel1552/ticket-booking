package ticketbooking.checkout;

import ticketbooking.audit.AuditEntry;
import ticketbooking.audit.AuditEntryRepository;
import ticketbooking.audit.AuditKind;
import ticketbooking.clock.AppClock;
import ticketbooking.hold.CustomerOrder;
import ticketbooking.hold.CustomerOrderRepository;
import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.refund.Refund;
import ticketbooking.refund.RefundKind;
import ticketbooking.refund.RefundRepository;
import ticketbooking.refund.RefundStatus;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordPendingExceptionalRefund {

    private final AccountRepository accounts;
    private final CustomerOrderRepository orders;
    private final EventSeatRepository eventSeats;
    private final RefundRepository refunds;
    private final AuditEntryRepository auditEntries;
    private final AppClock clock;
    private final EntityManager entityManager;

    public RecordPendingExceptionalRefund(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            RefundRepository refunds,
            AuditEntryRepository auditEntries,
            AppClock clock,
            EntityManager entityManager) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.refunds = refunds;
        this.auditEntries = auditEntries;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RecordedExceptionalRefund record(long orderId, int amountCents, String reason, String adminEmail) {
        Account admin = accounts.findByEmail(adminEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<EventSeat> claims = eventSeats.lockActiveOnOrder(orderId);
        if (!hasTicket(claims) || !refunds.findNeedingLookup(orderId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        Instant now = clock.now();
        Refund refund = refunds.save(new Refund(
                order, amountCents, RefundStatus.PENDING, now, RefundKind.EXCEPTIONAL));
        auditEntries.save(new AuditEntry(
                admin, order.getEvent(), null, AuditKind.EXCEPTIONAL_REFUND, reason, now));
        entityManager.flush();
        return new RecordedExceptionalRefund(refund.getId(), order.getCustomer().getEmail());
    }

    private static boolean hasTicket(List<EventSeat> claims) {
        for (EventSeat claim : claims) {
            if (claim.getKind() == EventSeatKind.TICKET) {
                return true;
            }
        }
        return false;
    }
}
