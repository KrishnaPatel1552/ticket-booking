package com.copart.prep.waitlist;

import com.copart.prep.block.SeatBlockRepository;
import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.SeatRowLock;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferWaitlist {

    private static final Duration HOLD_FOR = Duration.ofMinutes(15);
    private static final int ORDER_CAP = 6;
    private static final int EVENT_CAP = 12;

    private final WaitlistEntryRepository entries;
    private final EventSeatRepository eventSeats;
    private final SeatRepository seats;
    private final SeatBlockRepository seatBlocks;
    private final CustomerOrderRepository orders;
    private final PaymentRepository payments;
    private final AppClock clock;
    private final EntityManager entityManager;
    private final SeatRowLock seatRowLock;

    public OfferWaitlist(
            WaitlistEntryRepository entries,
            EventSeatRepository eventSeats,
            SeatRepository seats,
            SeatBlockRepository seatBlocks,
            CustomerOrderRepository orders,
            PaymentRepository payments,
            AppClock clock,
            EntityManager entityManager,
            SeatRowLock seatRowLock) {
        this.entries = entries;
        this.eventSeats = eventSeats;
        this.seats = seats;
        this.seatBlocks = seatBlocks;
        this.orders = orders;
        this.payments = payments;
        this.clock = clock;
        this.entityManager = entityManager;
        this.seatRowLock = seatRowLock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void offer(long eventId, List<EventSeat> released) {
        if (released.isEmpty()) {
            return;
        }
        entityManager.flush();
        List<EventSeat> freed = new ArrayList<>(released);
        freed.sort(Comparator.comparing(claim -> claim.getSeat().getId()));
        for (EventSeat claim : freed) {
            offerSeat(eventId, claim.getSeat().getId());
        }
    }

    private void offerSeat(long eventId, long seatId) {
        if (!free(eventId, seatId)) {
            return;
        }
        for (WaitlistEntry entry : entries.findByEventIdOrderByIdAsc(eventId)) {
            if (!room(entry)) {
                continue;
            }
            hold(entry, seatId);
            return;
        }
    }

    private boolean room(WaitlistEntry entry) {
        Account customer = entry.getCustomer();
        Event event = entry.getEvent();
        lockCustomerEvent(customer.getId(), event.getId());
        CustomerOrder order = openOrder(customer.getId(), event.getId());
        long onOrder = order == null ? 0 : eventSeats.countActiveOnOrder(order.getId());
        long atEvent = eventSeats.countActiveForCustomerAtEvent(customer.getId(), event.getId());
        long remaining = Math.min(ORDER_CAP - onOrder, EVENT_CAP - atEvent);
        return remaining >= 1;
    }

    private void hold(WaitlistEntry entry, long seatId) {
        seatRowLock.lockInIdOrder(List.of(seatId));
        Event event = entry.getEvent();
        if (!free(event.getId(), seatId)) {
            return;
        }
        Account customer = entry.getCustomer();
        Instant now = clock.now();
        CustomerOrder order = openOrder(customer.getId(), event.getId());
        if (order == null) {
            order = orders.save(new CustomerOrder(customer, event, now));
        }
        Seat seat = seats.findById(seatId).orElseThrow();
        EventSeat claim = new EventSeat(order, event, seat, EventSeatKind.HOLD, now.plus(HOLD_FOR));
        claim.markFromWaitlist();
        eventSeats.save(claim);
        entityManager.flush();
    }

    private CustomerOrder openOrder(long customerId, long eventId) {
        CustomerOrder order = orders.findByCustomerIdAndEventIdAndOpenForHoldsTrue(customerId, eventId)
                .orElse(null);
        if (order != null && payments.existsForOrder(order.getId())) {
            order.closeForHolds();
            entityManager.flush();
            return null;
        }
        return order;
    }

    private boolean free(long eventId, long seatId) {
        if (eventSeats.findByEventIdAndSeatIdAndActiveTrue(eventId, seatId).isPresent()) {
            return false;
        }
        return seatBlocks.findByEventIdAndSeatIdAndActiveTrue(eventId, seatId).isEmpty();
    }

    private void lockCustomerEvent(long customerId, long eventId) {
        entityManager.createNativeQuery("""
                insert into customer_event (customer_id, event_id)
                values (:customerId, :eventId)
                on conflict (customer_id, event_id) do nothing
                """)
                .setParameter("customerId", customerId)
                .setParameter("eventId", eventId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                select customer_id
                from customer_event
                where customer_id = :customerId
                  and event_id = :eventId
                for update
                """)
                .setParameter("customerId", customerId)
                .setParameter("eventId", eventId)
                .getSingleResult();
    }
}
