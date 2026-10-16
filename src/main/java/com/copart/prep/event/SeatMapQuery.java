package com.copart.prep.event;

import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.hold.ExpiredHoldRelease;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.SectionRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeatMapQuery {

    private final EventRepository events;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final EventSeatRepository eventSeats;
    private final ExpiredHoldRelease expiredHoldRelease;

    public SeatMapQuery(
            EventRepository events,
            SectionRepository sections,
            SeatRepository seats,
            EventSeatRepository eventSeats,
            ExpiredHoldRelease expiredHoldRelease) {
        this.events = events;
        this.sections = sections;
        this.seats = seats;
        this.eventSeats = eventSeats;
        this.expiredHoldRelease = expiredHoldRelease;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventSeatMap open(long eventId) {
        expiredHoldRelease.release(eventId);
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Map<Long, EventSeatKind> active = eventSeats.findByEventIdAndActiveTrue(event.getId()).stream()
                .collect(Collectors.toMap(claim -> claim.getSeat().getId(), EventSeat::getKind));
        List<SectionSeats> sectionMaps = sections.findByVenueIdOrderByIdAsc(event.getVenue().getId()).stream()
                .map(section -> new SectionSeats(
                        section.getName(),
                        seats.findBySectionIdOrderByIdAsc(section.getId()).stream()
                                .map(seat -> new SeatView(
                                        seat.getId(),
                                        seat.getLabel(),
                                        seat.getPriceTier().getPriceCents(),
                                        seat.getPriceTier().getEarlyBirdPriceCents(),
                                        stateOf(seat, active)))
                                .toList()))
                .toList();
        return new EventSeatMap(
                event.getId(),
                event.getName(),
                event.getVenue().getId(),
                event.getVenue().getName(),
                event.getStartsAt(),
                event.getTimeZone(),
                event.getEarlyBirdStartsAt(),
                event.getEarlyBirdEndsAt(),
                sectionMaps);
    }

    private static SeatState stateOf(Seat seat, Map<Long, EventSeatKind> active) {
        EventSeatKind kind = active.get(seat.getId());
        if (kind == null) {
            return SeatState.FREE;
        }
        return kind.seatState();
    }
}
