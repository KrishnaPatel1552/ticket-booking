package ticketbooking.hold;

import ticketbooking.clock.AppClock;
import ticketbooking.waitlist.OfferWaitlist;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExpiredHoldRelease {

    private final EventSeatRepository eventSeats;
    private final AppClock clock;
    private final OfferWaitlist offerWaitlist;

    public ExpiredHoldRelease(EventSeatRepository eventSeats, AppClock clock, OfferWaitlist offerWaitlist) {
        this.eventSeats = eventSeats;
        this.clock = clock;
        this.offerWaitlist = offerWaitlist;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void release(long eventId) {
        List<EventSeat> expired = eventSeats.lockExpiredHolds(eventId, EventSeatKind.HOLD, clock.now());
        for (EventSeat claim : expired) {
            claim.release();
        }
        offerWaitlist.offer(eventId, expired);
    }
}
