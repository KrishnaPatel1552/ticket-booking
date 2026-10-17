package ticketbooking.checkout;

import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.refund.Refund;
import ticketbooking.refund.RefundKind;
import ticketbooking.refund.RefundRepository;
import ticketbooking.refund.RefundStatus;
import ticketbooking.waitlist.OfferWaitlist;
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
    private final OfferWaitlist offerWaitlist;

    public FinishRefund(RefundRepository refunds, EventSeatRepository eventSeats, OfferWaitlist offerWaitlist) {
        this.refunds = refunds;
        this.eventSeats = eventSeats;
        this.offerWaitlist = offerWaitlist;
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
        if (refund.getKind() == RefundKind.EVENT_CANCELLATION) {
            releaseTickets(refund);
            return;
        }
        List<EventSeat> locked = releaseTickets(refund);
        offerWaitlist.offer(refund.getOrder().getEvent().getId(), locked);
    }

    private List<EventSeat> releaseTickets(Refund refund) {
        List<Long> seatIds = new ArrayList<>();
        for (EventSeat ticket : refund.getTickets()) {
            if (ticket.isActive()) {
                seatIds.add(ticket.getSeat().getId());
            }
        }
        if (refund.getKind() != RefundKind.EVENT_CANCELLATION && seatIds.size() != refund.getTickets().size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        List<EventSeat> locked = seatIds.isEmpty()
                ? List.of()
                : eventSeats.lockActiveForSeats(refund.getOrder().getId(), seatIds);
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
        return locked;
    }
}
