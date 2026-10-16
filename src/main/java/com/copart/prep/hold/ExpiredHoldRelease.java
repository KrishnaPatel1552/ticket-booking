package com.copart.prep.hold;

import com.copart.prep.clock.AppClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiredHoldRelease {

    private final EventSeatRepository eventSeats;
    private final AppClock clock;

    public ExpiredHoldRelease(EventSeatRepository eventSeats, AppClock clock) {
        this.eventSeats = eventSeats;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(long eventId) {
        for (EventSeat claim : eventSeats.lockExpiredHolds(eventId, EventSeatKind.HOLD, clock.now())) {
            claim.release();
        }
    }
}
