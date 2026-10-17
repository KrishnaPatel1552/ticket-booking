package com.copart.prep.checkout;

import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.Payment;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.payment.PaymentStatus;
import com.copart.prep.waitlist.OfferWaitlist;
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
    private final RecordEventCancellationRefund recordEventCancellationRefund;

    public FinishPayment(
            PaymentRepository payments,
            EventSeatRepository eventSeats,
            OfferWaitlist offerWaitlist,
            RecordEventCancellationRefund recordEventCancellationRefund) {
        this.payments = payments;
        this.eventSeats = eventSeats;
        this.offerWaitlist = offerWaitlist;
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
        List<EventSeat> written = new ArrayList<>();
        for (EventSeat claim : eventSeats.lockActiveOnOrder(payment.getOrder().getId())) {
            if (claim.getKind() == EventSeatKind.HOLD) {
                claim.becomeTicket();
                written.add(claim);
            }
        }
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
        refundIfEventCancelled(payment, holds);
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
        List<EventSeat> holds = eventSeats.lockActiveForSeats(payment.getOrder().getId(), seatIds);
        payment.mark(PaymentStatus.DECLINED);
        for (EventSeat hold : holds) {
            hold.release();
        }
        offerWaitlist.offer(payment.getOrder().getEvent().getId(), holds);
    }
}
