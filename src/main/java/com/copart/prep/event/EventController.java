package com.copart.prep.event;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

    private final EventSearch eventSearch;

    public EventController(EventSearch eventSearch) {
        this.eventSearch = eventSearch;
    }

    @GetMapping("/api/events")
    public List<EventSummary> search(@RequestParam String name) {
        return eventSearch.search(name);
    }
}
