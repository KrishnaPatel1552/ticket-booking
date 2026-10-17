package com.copart.prep.waitlist;

import com.copart.prep.block.SeatBlockRepository;
import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.SectionRepository;
import jakarta.persistence.EntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class JoinWaitlist {

    private final AccountRepository accounts;
    private final EventRepository events;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final EventSeatRepository eventSeats;
    private final SeatBlockRepository seatBlocks;
    private final WaitlistEntryRepository entries;
    private final EntityManager entityManager;

    public JoinWaitlist(
            AccountRepository accounts,
            EventRepository events,
            SectionRepository sections,
            SeatRepository seats,
            EventSeatRepository eventSeats,
            SeatBlockRepository seatBlocks,
            WaitlistEntryRepository entries,
            EntityManager entityManager) {
        this.accounts = accounts;
        this.events = events;
        this.sections = sections;
        this.seats = seats;
        this.eventSeats = eventSeats;
        this.seatBlocks = seatBlocks;
        this.entries = entries;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WaitlistView join(long eventId, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (entries.findByEventIdAndCustomerId(event.getId(), customer.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        if (anySeatFree(event)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        try {
            WaitlistEntry entry = entries.save(new WaitlistEntry(event, customer));
            entityManager.flush();
            return view(entry);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public WaitlistView read(long eventId, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        WaitlistEntry entry = entries.findByEventIdAndCustomerId(event.getId(), customer.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return view(entry);
    }

    private WaitlistView view(WaitlistEntry entry) {
        long place = entries.countByEventIdAndIdLessThanEqual(entry.getEvent().getId(), entry.getId());
        Long orderId = eventSeats.findActiveWaitlistHolds(entry.getCustomer().getId(), entry.getEvent().getId())
                .stream()
                .map(claim -> claim.getOrder().getId())
                .findFirst()
                .orElse(null);
        return new WaitlistView((int) place, orderId);
    }

    private boolean anySeatFree(Event event) {
        return sections.findByVenueIdOrderByIdAsc(event.getVenue().getId()).stream()
                .flatMap(section -> seats.findBySectionIdOrderByIdAsc(section.getId()).stream())
                .anyMatch(seat -> free(event.getId(), seat));
    }

    private boolean free(long eventId, Seat seat) {
        if (eventSeats.findByEventIdAndSeatIdAndActiveTrue(eventId, seat.getId()).isPresent()) {
            return false;
        }
        return seatBlocks.findByEventIdAndSeatIdAndActiveTrue(eventId, seat.getId()).isEmpty();
    }
}
