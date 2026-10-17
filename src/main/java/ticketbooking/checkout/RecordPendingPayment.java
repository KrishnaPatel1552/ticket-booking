package ticketbooking.checkout;

import ticketbooking.clock.AppClock;
import ticketbooking.event.Event;
import ticketbooking.hold.CustomerOrder;
import ticketbooking.hold.CustomerOrderRepository;
import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.payment.Payment;
import ticketbooking.payment.PaymentRepository;
import ticketbooking.payment.PaymentStatus;
import ticketbooking.pricetier.PriceTier;
import ticketbooking.promo.PromoCode;
import ticketbooking.promo.PromoCodeLockGate;
import ticketbooking.saleterms.SaleTerms;
import ticketbooking.saleterms.SaleTermsRepository;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final PromoCodeLockGate promoCodeLockGate;

    public RecordPendingPayment(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            PaymentRepository payments,
            SaleTermsRepository saleTerms,
            AppClock clock,
            EntityManager entityManager,
            PromoCodeLockGate promoCodeLockGate) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.payments = payments;
        this.saleTerms = saleTerms;
        this.clock = clock;
        this.entityManager = entityManager;
        this.promoCodeLockGate = promoCodeLockGate;
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
        if (order.getEvent().isCancelled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        if (payments.existsForOrder(orderId) || seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        List<EventSeat> holds = eventSeats.lockActiveForSeats(orderId, seatIds);
        Instant now = clock.now();
        if (!liveHolds(holds, seatIds, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        SaleTerms terms = saleTerms();
        PromoCode applied = appliedPromo(order.getPromoCode(), customer.getId());
        boolean exclusive = applied != null && applied.isExclusive();
        List<EventSeat> pricedHolds = new ArrayList<>(holds);
        pricedHolds.sort(Comparator.comparing(EventSeat::getId));
        List<Integer> prices = new ArrayList<>();
        for (EventSeat hold : pricedHolds) {
            prices.add(startingPriceCents(hold, now, exclusive));
        }
        int groupRate = exclusive ? 0 : groupRateCents(terms, prices);
        int promoAmount = promoAmountCents(applied, prices, groupRate);
        assignShares(pricedHolds, prices, groupRate, promoAmount, terms.getServiceFeeCents());
        int amount = OrderAmount.cents(terms.getServiceFeeCents(), prices) - groupRate - promoAmount;
        order.closeForHolds();
        Payment payment = payments.save(new Payment(order, amount, PaymentStatus.PENDING, now, applied));
        entityManager.flush();
        return payment.getId();
    }

    private static void assignShares(
            List<EventSeat> holds,
            List<Integer> startingPrices,
            int groupRate,
            int promoAmount,
            int serviceFeeCents) {
        int count = holds.size();
        int[] weights = new int[count];
        long[] ticketIds = new long[count];
        int[] afterGroup = new int[count];
        int[] equal = new int[count];
        for (int index = 0; index < count; index++) {
            weights[index] = startingPrices.get(index);
            ticketIds[index] = holds.get(index).getId();
            equal[index] = 1;
        }
        int[] groupShares = CentShares.split(groupRate, weights, ticketIds);
        for (int index = 0; index < count; index++) {
            afterGroup[index] = weights[index] - groupShares[index];
        }
        int[] promoShares = CentShares.split(promoAmount, afterGroup, ticketIds);
        int[] feeShares = CentShares.split(serviceFeeCents, equal, ticketIds);
        for (int index = 0; index < count; index++) {
            holds.get(index).assignShares(weights[index], groupShares[index], promoShares[index], feeShares[index]);
        }
    }

    private PromoCode appliedPromo(PromoCode attached, long customerId) {
        if (attached == null) {
            return null;
        }
        entityManager.lock(attached, LockModeType.PESSIMISTIC_WRITE);
        entityManager.flush();
        promoCodeLockGate.afterPromoCodeLock();
        long taken = payments.countTaken(attached.getId());
        long takenByCustomer = payments.countTakenByCustomer(attached.getId(), customerId);
        if (taken >= attached.getGlobalLimit() || takenByCustomer >= attached.getPerCustomerLimit()) {
            return null;
        }
        return attached;
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

    private static int startingPriceCents(EventSeat hold, Instant now, boolean exclusive) {
        PriceTier tier = hold.getSeat().getPriceTier();
        if (exclusive) {
            return tier.getPriceCents();
        }
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

    private static int groupRateCents(SaleTerms terms, List<Integer> startingPrices) {
        if (startingPrices.size() < terms.getGroupRateThreshold()) {
            return 0;
        }
        int ticketTotal = 0;
        for (int startingPrice : startingPrices) {
            ticketTotal += startingPrice;
        }
        return Math.min(terms.getGroupRateAmountCents(), ticketTotal);
    }

    private static int promoAmountCents(PromoCode promoCode, List<Integer> startingPrices, int groupRate) {
        if (promoCode == null) {
            return 0;
        }
        int afterGroup = 0;
        for (int startingPrice : startingPrices) {
            afterGroup += startingPrice;
        }
        afterGroup -= groupRate;
        return Math.min(promoCode.getAmountCents(), Math.max(afterGroup, 0));
    }

    private SaleTerms saleTerms() {
        List<SaleTerms> terms = saleTerms.findAll();
        if (terms.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return terms.get(0);
    }
}
