package ticketbooking.sales;

import ticketbooking.block.SeatBlockRepository;
import ticketbooking.event.Event;
import ticketbooking.event.EventRepository;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.payment.PaymentRepository;
import ticketbooking.venue.SeatRepository;
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
        int free;
        int blockedCount;
        if (event.isCancelled()) {
            free = 0;
            blockedCount = (int) (venueSeats - held - tickets);
        } else {
            free = (int) (venueSeats - held - tickets - blocked);
            blockedCount = (int) blocked;
        }
        return new SalesView(
                free,
                (int) held,
                blockedCount,
                (int) tickets,
                (int) ticketsSold,
                (int) totalPaidCents);
    }
}
