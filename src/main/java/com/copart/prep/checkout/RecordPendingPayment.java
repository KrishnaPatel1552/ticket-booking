package com.copart.prep.checkout;

import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.Payment;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.payment.PaymentStatus;
import com.copart.prep.pricetier.PriceTier;
import com.copart.prep.saleterms.SaleTerms;
import com.copart.prep.saleterms.SaleTermsRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordPendingPayment {

    private final AccountRepository accounts;
    private final CustomerOrderRepository orders;
    private final EventSeatRepository eventSeats;
    private final PaymentRepository payments;
    private final SaleTermsRepository saleTerms;
    private final AppClock clock;
    private final EntityManager entityManager;

    public RecordPendingPayment(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            PaymentRepository payments,
            SaleTermsRepository saleTerms,
            AppClock clock,
            EntityManager entityManager) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.payments = payments;
        this.saleTerms = saleTerms;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long record(long orderId, List<Long> seatIds, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (payments.existsForOrder(orderId) || seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        List<EventSeat> holds = eventSeats.lockActiveForSeats(orderId, seatIds);
        Instant now = clock.now();
        if (!liveHolds(holds, seatIds, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        List<Integer> prices = new ArrayList<>();
        for (EventSeat hold : holds) {
            prices.add(startingPriceCents(hold, now));
        }
        int amount = OrderAmount.cents(serviceFeeCents(), prices);
        order.closeForHolds();
        Payment payment = payments.save(new Payment(order, amount, PaymentStatus.PENDING, now));
        entityManager.flush();
        return payment.getId();
    }

    private static boolean liveHolds(List<EventSeat> holds, List<Long> seatIds, Instant now) {
        if (holds.size() != seatIds.size()) {
            return false;
        }
        for (EventSeat hold : holds) {
            if (hold.getKind() != EventSeatKind.HOLD || hold.getHeldUntil() == null || !hold.getHeldUntil().isAfter(now)) {
                return false;
            }
        }
        return true;
    }

    private static int startingPriceCents(EventSeat hold, Instant now) {
        PriceTier tier = hold.getSeat().getPriceTier();
        Event event = hold.getEvent();
        Integer earlyBirdPriceCents = tier.getEarlyBirdPriceCents();
        Instant startsAt = event.getEarlyBirdStartsAt();
        Instant endsAt = event.getEarlyBirdEndsAt();
        if (earlyBirdPriceCents == null || startsAt == null || endsAt == null) {
            return tier.getPriceCents();
        }
        if (!now.isBefore(startsAt) && now.isBefore(endsAt)) {
            return earlyBirdPriceCents;
        }
        return tier.getPriceCents();
    }

    private int serviceFeeCents() {
        List<SaleTerms> terms = saleTerms.findAll();
        if (terms.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return terms.get(0).getServiceFeeCents();
    }
}
