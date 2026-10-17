package ticketbooking.hold;

import ticketbooking.checkout.OrderQuery;
import ticketbooking.checkout.OrderView;
import ticketbooking.payment.Payment;
import ticketbooking.payment.PaymentRepository;
import ticketbooking.payment.PaymentStatus;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
import ticketbooking.waitlist.OfferWaitlist;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReleaseHold {

    private final AccountRepository accounts;
    private final CustomerOrderRepository orders;
    private final EventSeatRepository eventSeats;
    private final PaymentRepository payments;
    private final OfferWaitlist offerWaitlist;
    private final OrderQuery orderQuery;

    public ReleaseHold(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            PaymentRepository payments,
            OfferWaitlist offerWaitlist,
            OrderQuery orderQuery) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.payments = payments;
        this.offerWaitlist = offerWaitlist;
        this.orderQuery = orderQuery;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderView releaseAll(long orderId, String email) {
        CustomerOrder order = ownedOrder(orderId, email);
        ensureNotLockedByPayment(orderId);
        List<EventSeat> claims = eventSeats.lockActiveOnOrder(orderId);
        List<EventSeat> released = new ArrayList<>();
        for (EventSeat claim : claims) {
            if (claim.getKind() == EventSeatKind.HOLD) {
                claim.release();
                released.add(claim);
            }
        }
        if (released.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        if (eventSeats.countActiveOnOrder(orderId) == 0 && !payments.existsForOrder(orderId)) {
            order.attachPromoCode(null);
        }
        offerWaitlist.offer(order.getEvent().getId(), released);
        return orderQuery.open(orderId, email);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderView releaseSeat(long orderId, long seatId, String email) {
        CustomerOrder order = ownedOrder(orderId, email);
        ensureNotLockedByPayment(orderId);
        List<EventSeat> claims = eventSeats.lockActiveForSeats(orderId, List.of(seatId));
        if (claims.size() != 1 || claims.get(0).getKind() != EventSeatKind.HOLD) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        EventSeat claim = claims.get(0);
        claim.release();
        if (eventSeats.countActiveOnOrder(orderId) == 0 && !payments.existsForOrder(orderId)) {
            order.attachPromoCode(null);
        }
        offerWaitlist.offer(order.getEvent().getId(), List.of(claim));
        return orderQuery.open(orderId, email);
    }

    private CustomerOrder ownedOrder(long orderId, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return order;
    }

    private void ensureNotLockedByPayment(long orderId) {
        Payment payment = payments.findByOrder_Id(orderId).orElse(null);
        if (payment != null && payment.getStatus() != PaymentStatus.DECLINED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }
}
