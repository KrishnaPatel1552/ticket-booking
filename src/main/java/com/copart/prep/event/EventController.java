package com.copart.prep.event;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventSearch eventSearch;
    private final SeatMapQuery seatMapQuery;

    public EventController(EventSearch eventSearch, SeatMapQuery seatMapQuery) {
        this.eventSearch = eventSearch;
        this.seatMapQuery = seatMapQuery;
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
