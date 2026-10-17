package com.copart.prep.sales;

import com.copart.prep.block.SeatBlockRepository;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.venue.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesOccupancy {

    private final EventRepository events;
    private final SeatRepository seats;
    private final EventSeatRepository eventSeats;
    private final SeatBlockRepository seatBlocks;
    private final PaymentRepository payments;

    public SalesOccupancy(
            EventRepository events,
            SeatRepository seats,
            EventSeatRepository eventSeats,
            SeatBlockRepository seatBlocks,
            PaymentRepository payments) {
        this.events = events;
        this.seats = seats;
        this.eventSeats = eventSeats;
        this.seatBlocks = seatBlocks;
        this.payments = payments;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public SalesView read(long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long held = eventSeats.countByEventIdAndActiveTrueAndKind(eventId, EventSeatKind.HOLD);
        long tickets = eventSeats.countByEventIdAndActiveTrueAndKind(eventId, EventSeatKind.TICKET);
        long blocked = seatBlocks.countByEventIdAndActiveTrue(eventId);
        long venueSeats = seats.countBySection_Venue_Id(event.getVenue().getId());
        long ticketsSold = eventSeats.countByEventIdAndKind(eventId, EventSeatKind.TICKET);
        long totalPaidCents = payments.sumSucceededAmountCents(eventId);
        int free = event.isCancelled() ? 0 : (int) (venueSeats - held - tickets - blocked);
        return new SalesView(
                free,
                (int) held,
                (int) blocked,
                (int) tickets,
                (int) ticketsSold,
                (int) totalPaidCents);
    }
}
