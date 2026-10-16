package com.copart.prep.checkout;

import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.Payment;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.payment.PaymentStatus;
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

    public FinishPayment(PaymentRepository payments, EventSeatRepository eventSeats) {
        this.payments = payments;
        this.eventSeats = eventSeats;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void apply(long paymentId, List<Long> seatIds, PaymentMockResult result) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        switch (result) {
            case SUCCESS -> succeed(payment, seatIds);
            case DECLINE -> decline(payment, seatIds);
            case TIMEOUT, UNKNOWN -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
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
    }

    private void decline(Payment payment, List<Long> seatIds) {
        List<EventSeat> holds = eventSeats.lockActiveForSeats(payment.getOrder().getId(), seatIds);
        payment.mark(PaymentStatus.DECLINED);
        for (EventSeat hold : holds) {
            hold.release();
        }
    }
}
