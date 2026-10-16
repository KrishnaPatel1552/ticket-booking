package com.copart.prep.block;

import com.copart.prep.audit.AuditEntry;
import com.copart.prep.audit.AuditEntryRepository;
import com.copart.prep.audit.AuditKind;
import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.hold.HoldGate;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.SeatRowLock;
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

    public BlockSeat(
            AccountRepository accounts,
            EventRepository events,
            SeatRepository seats,
            EventSeatRepository eventSeats,
            SeatBlockRepository seatBlocks,
            AuditEntryRepository auditEntries,
            SeatRowLock seatRowLock,
            HoldGate holdGate,
            AppClock clock) {
        this.accounts = accounts;
        this.events = events;
        this.seats = seats;
        this.eventSeats = eventSeats;
        this.seatBlocks = seatBlocks;
        this.auditEntries = auditEntries;
        this.seatRowLock = seatRowLock;
        this.holdGate = holdGate;
        this.clock = clock;
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
