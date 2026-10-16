package com.copart.prep.hold;

import com.copart.prep.block.SeatBlockRepository;
import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.SeatRowLock;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class HoldSeats {

    private static final Duration HOLD_FOR = Duration.ofMinutes(10);
    private static final int ORDER_CAP = 6;
    private static final int EVENT_CAP = 12;

    private final AccountRepository accounts;
    private final EventRepository events;
    private final SeatRepository seats;
    private final CustomerOrderRepository orders;
    private final EventSeatRepository eventSeats;
    private final SeatBlockRepository seatBlocks;
    private final PaymentRepository payments;
    private final AppClock clock;
    private final EntityManager entityManager;
    private final HoldGate holdGate;
    private final SeatRowLock seatRowLock;

    public HoldSeats(
            AccountRepository accounts,
            EventRepository events,
            SeatRepository seats,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            SeatBlockRepository seatBlocks,
            PaymentRepository payments,
            AppClock clock,
            EntityManager entityManager,
            HoldGate holdGate,
            SeatRowLock seatRowLock) {
        this.accounts = accounts;
        this.events = events;
        this.seats = seats;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.seatBlocks = seatBlocks;
        this.payments = payments;
        this.clock = clock;
        this.entityManager = entityManager;
        this.holdGate = holdGate;
        this.seatRowLock = seatRowLock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HoldSeatsResponse hold(long eventId, List<Long> seatIds, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<Seat> chosen = new ArrayList<>();
        for (Long seatId : seatIds) {
            Seat seat = seats.findById(seatId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
            if (!seat.getSection().getVenue().getId().equals(event.getVenue().getId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            chosen.add(seat);
        }
        lockCustomerEvent(customer.getId(), event.getId());
        Instant now = clock.now();
        CustomerOrder order = orders.findByCustomerIdAndEventIdAndOpenForHoldsTrue(customer.getId(), event.getId())
                .orElse(null);
        if (order != null && payments.existsForOrder(order.getId())) {
            order.closeForHolds();
            entityManager.flush();
            order = null;
        }
        long onOrder = order == null ? 0 : eventSeats.countActiveOnOrder(order.getId());
        long atEvent = eventSeats.countActiveForCustomerAtEvent(customer.getId(), event.getId());
        long remaining = Math.min(ORDER_CAP - onOrder, EVENT_CAP - atEvent);
        if (seatIds.size() > remaining) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        if (order == null) {
            order = orders.save(new CustomerOrder(customer, event, now));
        }
        Instant heldUntil = now.plus(HOLD_FOR);
        holdGate.beforeWritingSeats();
        seatRowLock.lockInIdOrder(chosen.stream().map(Seat::getId).toList());
        holdGate.afterSeatLock();
        for (Seat seat : chosen) {
            if (seatBlocks.findByEventIdAndSeatIdAndActiveTrue(event.getId(), seat.getId()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT);
            }
        }
        try {
            for (Seat seat : chosen) {
                eventSeats.save(new EventSeat(order, event, seat, EventSeatKind.HOLD, heldUntil));
            }
            entityManager.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return new HoldSeatsResponse(order.getId(), List.copyOf(seatIds), heldUntil);
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
        holdGate.afterCustomerEventLock();
    }
}
