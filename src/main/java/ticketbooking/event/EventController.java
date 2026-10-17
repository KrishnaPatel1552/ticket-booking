package ticketbooking.event;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventSearch eventSearch;
    private final SeatMapQuery seatMapQuery;
    private final EventCreator eventCreator;

    public EventController(EventSearch eventSearch, SeatMapQuery seatMapQuery, EventCreator eventCreator) {
        this.eventSearch = eventSearch;
        this.seatMapQuery = seatMapQuery;
        this.eventCreator = eventCreator;
    }

    @PostMapping("/api/admin/events")
    @ResponseStatus(HttpStatus.CREATED)
    public EventSummary create(@Valid @RequestBody CreateEventRequest request) {
        return eventCreator.create(request);
    }

    @GetMapping("/api/events")
    public List<EventSummary> search(@RequestParam String name) {
        return eventSearch.search(name);
    }

    @GetMapping("/api/events/{eventId}")
    public EventSeatMap open(@PathVariable long eventId) {
        return seatMapQuery.open(eventId);
    }
}
