package ticketbooking.hold;

import ticketbooking.clock.AppClock;
import ticketbooking.payment.PaymentRepository;
import ticketbooking.waitlist.OfferWaitlist;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiredHoldRelease {

    private final EventSeatRepository eventSeats;
    private final PaymentRepository payments;
    private final AppClock clock;
    private final OfferWaitlist offerWaitlist;

    public ExpiredHoldRelease(
            EventSeatRepository eventSeats,
            PaymentRepository payments,
            AppClock clock,
            OfferWaitlist offerWaitlist) {
        this.eventSeats = eventSeats;
        this.payments = payments;
        this.clock = clock;
        this.offerWaitlist = offerWaitlist;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(long eventId) {
        List<EventSeat> expired = eventSeats.lockExpiredHolds(eventId, EventSeatKind.HOLD, clock.now());
        if (expired.isEmpty()) {
            return;
        }
        Set<CustomerOrder> touchedOrders = new LinkedHashSet<>();
        for (EventSeat claim : expired) {
            claim.release();
            if (claim.getOrder() != null) {
                touchedOrders.add(claim.getOrder());
            }
        }
        for (CustomerOrder order : touchedOrders) {
            if (order.isOpenForHolds()
                    && eventSeats.countActiveOnOrder(order.getId()) == 0
                    && !payments.existsForOrder(order.getId())) {
                order.attachPromoCode(null);
            }
        }
        offerWaitlist.offer(eventId, expired);
    }
}
