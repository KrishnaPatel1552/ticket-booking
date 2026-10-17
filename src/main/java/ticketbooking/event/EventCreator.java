package ticketbooking.event;

import ticketbooking.venue.Venue;
import ticketbooking.venue.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventCreator {

    private final EventRepository events;
    private final VenueRepository venues;

    public EventCreator(EventRepository events, VenueRepository venues) {
        this.events = events;
        this.venues = venues;
    }

    @Transactional
    public EventSummary create(CreateEventRequest request) {
        Venue venue = venues.findById(request.venueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Event event = events.save(new Event(
                venue,
                request.name(),
                request.startsAt(),
                request.timeZone(),
                request.earlyBirdStartsAt(),
                request.earlyBirdEndsAt()));
        return new EventSummary(
                event.getId(),
                event.getName(),
                venue.getId(),
                venue.getName(),
                event.getStartsAt(),
                event.getTimeZone());
    }
}
