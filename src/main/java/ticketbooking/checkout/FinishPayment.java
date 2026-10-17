package ticketbooking.checkout;

import ticketbooking.clock.AppClock;
import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.payment.Payment;
import ticketbooking.payment.PaymentRepository;
import ticketbooking.payment.PaymentStatus;
import ticketbooking.waitlist.OfferWaitlist;
import ticketbooking.waitlist.WaitlistEntryRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinishPayment {

    private final PaymentRepository payments;
    private final EventSeatRepository eventSeats;
    private final OfferWaitlist offerWaitlist;
    private final WaitlistEntryRepository waitlistEntries;
    private final AppClock clock;
    private final RecordEventCancellationRefund recordEventCancellationRefund;

    public FinishPayment(
            PaymentRepository payments,
            EventSeatRepository eventSeats,
            OfferWaitlist offerWaitlist,
            WaitlistEntryRepository waitlistEntries,
            AppClock clock,
            RecordEventCancellationRefund recordEventCancellationRefund) {
        this.payments = payments;
        this.eventSeats = eventSeats;
        this.offerWaitlist = offerWaitlist;
        this.waitlistEntries = waitlistEntries;
        this.clock = clock;
        this.recordEventCancellationRefund = recordEventCancellationRefund;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(long paymentId, List<Long> seatIds, PaymentMockResult result) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        switch (result) {
            case SUCCESS -> succeed(payment, seatIds);
            case DECLINE -> decline(payment, seatIds);
            case TIMEOUT, UNKNOWN -> payment.mark(PaymentStatus.UNKNOWN);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void applyLookup(long paymentId, PaymentMockResult result) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (payments.findNeedingReconcile(payment.getOrder().getId()).isEmpty()) {
            return;
        }
        switch (result) {
            case SUCCESS -> writeTickets(payment);
            case DECLINE -> releaseSeats(payment);
            case TIMEOUT, UNKNOWN -> payment.mark(PaymentStatus.UNKNOWN);
        }
    }

    private void writeTickets(Payment payment) {
        payment.mark(PaymentStatus.SUCCEEDED);
        List<EventSeat> claims = eventSeats.lockActiveOnOrder(payment.getOrder().getId());
        boolean priced = false;
        for (EventSeat claim : claims) {
            if (claim.getKind() == EventSeatKind.HOLD && claim.getStartingPriceCents() != null) {
                priced = true;
                break;
            }
        }
        List<EventSeat> written = new ArrayList<>();
        for (EventSeat claim : claims) {
            if (claim.getKind() != EventSeatKind.HOLD) {
                continue;
            }
            if (priced && claim.getStartingPriceCents() == null) {
                continue;
            }
            claim.becomeTicket();
            written.add(claim);
        }
        endWaitlistIfFulfilled(payment, written);
        refundIfEventCancelled(payment, written);
    }

    private void releaseSeats(Payment payment) {
        payment.mark(PaymentStatus.DECLINED);
        List<EventSeat> claims = eventSeats.lockActiveOnOrder(payment.getOrder().getId());
        for (EventSeat claim : claims) {
            claim.release();
        }
        offerWaitlist.offer(payment.getOrder().getEvent().getId(), claims);
    }

    private void succeed(Payment payment, List<Long> seatIds) {
        List<EventSeat> holds = eventSeats.lockActiveForSeats(payment.getOrder().getId(), seatIds);
        if (holds.size() != seatIds.size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        payment.mark(PaymentStatus.SUCCEEDED);
        for (EventSeat hold : holds) {
            if (hold.getKind() != EventSeatKind.HOLD) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
            hold.becomeTicket();
        }
        endWaitlistIfFulfilled(payment, holds);
        refundIfEventCancelled(payment, holds);
    }

    private void endWaitlistIfFulfilled(Payment payment, List<EventSeat> tickets) {
        boolean fromWaitlist = tickets.stream().anyMatch(EventSeat::isFromWaitlist);
        if (!fromWaitlist) {
            return;
        }
        long eventId = payment.getOrder().getEvent().getId();
        long customerId = payment.getOrder().getCustomer().getId();
        waitlistEntries.findByEventIdAndCustomerIdAndEndedAtIsNull(eventId, customerId)
                .ifPresent(entry -> entry.end(clock.now()));
    }

    private void refundIfEventCancelled(Payment payment, List<EventSeat> tickets) {
        if (!payment.getOrder().getEvent().isCancelled()) {
            return;
        }
        for (EventSeat ticket : tickets) {
            recordEventCancellationRefund.record(ticket.getId());
        }
    }

    private void decline(Payment payment, List<Long> seatIds) {
        List<EventSeat> holds = eventSeats.lockActiveOnOrder(payment.getOrder().getId());
        payment.mark(PaymentStatus.DECLINED);
        List<EventSeat> released = new ArrayList<>();
        for (EventSeat hold : holds) {
            if (hold.getKind() == EventSeatKind.HOLD) {
                hold.release();
                released.add(hold);
            }
        }
        offerWaitlist.offer(payment.getOrder().getEvent().getId(), released);
    }
}
