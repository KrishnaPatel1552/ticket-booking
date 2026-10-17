package com.copart.prep.checkout;

import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.refund.Refund;
import com.copart.prep.refund.RefundKind;
import com.copart.prep.refund.RefundRepository;
import com.copart.prep.refund.RefundStatus;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinishRefund {

    private final RefundRepository refunds;
    private final EventSeatRepository eventSeats;

    public FinishRefund(RefundRepository refunds, EventSeatRepository eventSeats) {
        this.refunds = refunds;
        this.eventSeats = eventSeats;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(long refundId, PaymentMockResult result) {
        Refund refund = refunds.findWithTickets(refundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        switch (result) {
            case SUCCESS -> succeed(refund);
            case DECLINE -> refund.mark(RefundStatus.FAILED);
            case TIMEOUT, UNKNOWN -> refund.mark(RefundStatus.UNKNOWN);
            default -> {
                PaymentMockResult unexpected = result;
                throw new IllegalStateException(unexpected.name());
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyLookup(long refundId, PaymentMockResult result) {
        Refund refund = refunds.findWithTickets(refundId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (refund.getStatus() != RefundStatus.PENDING && refund.getStatus() != RefundStatus.UNKNOWN) {
            return;
        }
        switch (result) {
            case SUCCESS -> succeed(refund);
            case DECLINE -> refund.mark(RefundStatus.FAILED);
            case TIMEOUT, UNKNOWN -> refund.mark(RefundStatus.UNKNOWN);
            default -> {
                PaymentMockResult unexpected = result;
                throw new IllegalStateException(unexpected.name());
            }
        }
    }

    private void succeed(Refund refund) {
        if (refund.getKind() == RefundKind.EXCEPTIONAL) {
            refund.mark(RefundStatus.SUCCEEDED);
            return;
        }
        List<Long> seatIds = new ArrayList<>();
        for (EventSeat ticket : refund.getTickets()) {
            seatIds.add(ticket.getSeat().getId());
        }
        List<EventSeat> locked = eventSeats.lockActiveForSeats(refund.getOrder().getId(), seatIds);
        if (locked.size() != seatIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        refund.mark(RefundStatus.SUCCEEDED);
        for (EventSeat ticket : locked) {
            if (ticket.getKind() != EventSeatKind.TICKET) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            ticket.release();
        }
    }
}
