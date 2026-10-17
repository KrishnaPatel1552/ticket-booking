package com.copart.prep.checkout;

import com.copart.prep.audit.AuditEntry;
import com.copart.prep.audit.AuditEntryRepository;
import com.copart.prep.audit.AuditKind;
import com.copart.prep.clock.AppClock;
import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.refund.Refund;
import com.copart.prep.refund.RefundKind;
import com.copart.prep.refund.RefundRepository;
import com.copart.prep.refund.RefundStatus;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
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
