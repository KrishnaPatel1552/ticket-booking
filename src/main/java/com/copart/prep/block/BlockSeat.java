package com.copart.prep.block;

import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BlockSeat {

    private final EventRepository events;
    private final SeatRepository seats;
    private final SeatBlockRepository seatBlocks;
    private final AppClock clock;

    public BlockSeat(
            EventRepository events,
            SeatRepository seats,
            SeatBlockRepository seatBlocks,
            AppClock clock) {
        this.events = events;
        this.seats = seats;
        this.seatBlocks = seatBlocks;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void block(long eventId, long seatId, String reason) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Seat seat = seats.findById(seatId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST));
        if (!seat.getSection().getVenue().getId().equals(event.getVenue().getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        seatBlocks.save(new SeatBlock(event, seat, clock.now()));
    }
}
