package ticketbooking.block;

import ticketbooking.audit.AuditEntry;
import ticketbooking.audit.AuditEntryRepository;
import ticketbooking.audit.AuditKind;
import ticketbooking.clock.AppClock;
import ticketbooking.event.Event;
import ticketbooking.event.EventRepository;
import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.hold.HoldGate;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
import ticketbooking.venue.Seat;
import ticketbooking.venue.SeatRepository;
import ticketbooking.venue.SeatRowLock;
import ticketbooking.waitlist.OfferWaitlist;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlockSeat {

    private final AccountRepository accounts;
    private final EventRepository events;
    private final SeatRepository seats;
    private final EventSeatRepository eventSeats;
    private final SeatBlockRepository seatBlocks;
    private final AuditEntryRepository auditEntries;
    private final SeatRowLock seatRowLock;
    private final HoldGate holdGate;
    private final AppClock clock;
    private final OfferWaitlist offerWaitlist;

    public BlockSeat(
            AccountRepository accounts,
            EventRepository events,
            SeatRepository seats,
            EventSeatRepository eventSeats,
            SeatBlockRepository seatBlocks,
            AuditEntryRepository auditEntries,
            SeatRowLock seatRowLock,
            HoldGate holdGate,
            AppClock clock,
            OfferWaitlist offerWaitlist) {
        this.accounts = accounts;
        this.events = events;
        this.seats = seats;
        this.eventSeats = eventSeats;
        this.seatBlocks = seatBlocks;
        this.auditEntries = auditEntries;
        this.seatRowLock = seatRowLock;
        this.holdGate = holdGate;
        this.clock = clock;
        this.offerWaitlist = offerWaitlist;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void block(long eventId, long seatId, String reason, String email) {
        Account admin = admin(email);
        Event event = event(eventId);
        Seat seat = seatAtEvent(seatId, event);
        seatRowLock.lockInIdOrder(List.of(seat.getId()));
        holdGate.afterSeatLock();
        if (eventSeats.findByEventIdAndSeatIdAndActiveTrue(event.getId(), seat.getId()).isPresent()
                || seatBlocks.findByEventIdAndSeatIdAndActiveTrue(event.getId(), seat.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        seatBlocks.save(new SeatBlock(event, seat, clock.now()));
        record(admin, event, seat, AuditKind.BLOCK, reason);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(long eventId, long seatId, String reason, String email) {
        Account admin = admin(email);
        Event event = event(eventId);
        Seat seat = seatAtEvent(seatId, event);
        seatRowLock.lockInIdOrder(List.of(seat.getId()));
        SeatBlock block = seatBlocks.findByEventIdAndSeatIdAndActiveTrue(event.getId(), seat.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT));
        block.release();
        record(admin, event, seat, AuditKind.RELEASE_BLOCK, reason);
        offerWaitlist.offer(event.getId(), List.of(new EventSeat(null, event, seat, EventSeatKind.HOLD, null)));
    }

    private Account admin(String email) {
        return accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private Event event(long eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Seat seatAtEvent(long seatId, Event event) {
        Seat seat = seats.findById(seatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        if (!seat.getSection().getVenue().getId().equals(event.getVenue().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        return seat;
    }

    private void record(Account admin, Event event, Seat seat, AuditKind kind, String reason) {
        auditEntries.save(new AuditEntry(admin, event, seat, kind, reason, clock.now()));
    }
}
