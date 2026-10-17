package com.copart.prep.checkout;

import com.copart.prep.clock.AppClock;
import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.refund.Refund;
import com.copart.prep.refund.RefundKind;
import com.copart.prep.refund.RefundRepository;
import com.copart.prep.refund.RefundStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordEventCancellationRefund {

    private final EventSeatRepository eventSeats;
    private final RefundRepository refunds;
    private final AppClock clock;
    private final EntityManager entityManager;

    public RecordEventCancellationRefund(
            EventSeatRepository eventSeats,
            RefundRepository refunds,
            AppClock clock,
            EntityManager entityManager) {
        this.eventSeats = eventSeats;
        this.refunds = refunds;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public List<Long> ticketsNeedingRefund(long eventId) {
        List<Long> ids = new ArrayList<>();
        for (EventSeat ticket : eventSeats.findTickets(eventId)) {
            if (needsRefund(ticket)) {
                ids.add(ticket.getId());
            }
        }
        return ids;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long record(long eventSeatId) {
        EventSeat ticket = eventSeats.findById(eventSeatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        entityManager.lock(ticket, LockModeType.PESSIMISTIC_WRITE);
        if (!needsRefund(ticket)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        Instant now = clock.now();
        Refund refund = new Refund(
                ticket.getOrder(),
                amountCents(ticket),
                RefundStatus.PENDING,
                now,
                RefundKind.EVENT_CANCELLATION);
        refund.addTicket(ticket);
        refunds.save(refund);
        entityManager.flush();
        return refund.getId();
    }

    private boolean needsRefund(EventSeat ticket) {
        if (ticket.getKind() != EventSeatKind.TICKET) {
            return false;
        }
        for (Refund refund : refunds.findEventCancellationsForTicket(ticket.getId())) {
            if (refund.getStatus() == RefundStatus.PENDING
                    || refund.getStatus() == RefundStatus.UNKNOWN
                    || refund.getStatus() == RefundStatus.SUCCEEDED) {
                return false;
            }
        }
        if (ticket.isActive()) {
            return !refunds.existsOpenForTickets(List.of(ticket.getId()));
        }
        return refunds.customerCancelSucceeded(ticket.getId());
    }

    private int amountCents(EventSeat ticket) {
        Integer failed = null;
        for (Refund refund : refunds.findEventCancellationsForTicket(ticket.getId())) {
            if (refund.getStatus() == RefundStatus.FAILED) {
                failed = refund.getAmountCents();
            }
        }
        if (failed != null) {
            return failed;
        }
        int fee = ticket.getFeeShareCents();
        if (!ticket.isActive()) {
            return fee;
        }
        int net = ticket.getStartingPriceCents() - ticket.getGroupShareCents() - ticket.getPromoShareCents();
        return net + fee;
    }
}
