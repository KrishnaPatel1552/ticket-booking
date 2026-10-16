package com.copart.prep.event;

import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.SectionRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeatMapQuery {

    private final EventRepository events;
    private final SectionRepository sections;
    private final SeatRepository seats;

    public SeatMapQuery(EventRepository events, SectionRepository sections, SeatRepository seats) {
        this.events = events;
        this.sections = sections;
        this.seats = seats;
    }

    @Transactional(readOnly = true)
    public EventSeatMap open(long eventId) {
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        List<SectionSeats> sectionMaps = sections.findByVenueIdOrderByIdAsc(event.getVenue().getId()).stream()
                .map(section -> new SectionSeats(
                        section.getName(),
                        seats.findBySectionIdOrderByIdAsc(section.getId()).stream()
                                .map(seat -> new SeatView(
                                        seat.getId(),
                                        seat.getLabel(),
                                        seat.getPriceTier().getPriceCents(),
                                        seat.getPriceTier().getEarlyBirdPriceCents(),
                                        SeatState.FREE))
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
}
