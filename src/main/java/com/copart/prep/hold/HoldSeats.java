package com.copart.prep.hold;

import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
    private final AppClock clock;

    public HoldSeats(
            AccountRepository accounts,
            EventRepository events,
            SeatRepository seats,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            AppClock clock) {
        this.accounts = accounts;
        this.events = events;
        this.seats = seats;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.clock = clock;
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
        Instant now = clock.now();
        CustomerOrder order = orders.findByCustomerIdAndEventIdAndOpenForHoldsTrue(customer.getId(), event.getId())
                .orElse(null);
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
        for (Seat seat : chosen) {
            eventSeats.save(new EventSeat(order, event, seat, EventSeatKind.HOLD, heldUntil));
        }
        return new HoldSeatsResponse(order.getId(), List.copyOf(seatIds), heldUntil);
    }
}
