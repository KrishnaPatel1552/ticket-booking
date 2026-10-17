package ticketbooking.event;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventSearch {

    private final EventRepository events;

    public EventSearch(EventRepository events) {
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<EventSummary> search(String name) {
        return events.findByNameContainingIgnoreCaseOrderByIdAsc(name).stream()
                .map(event -> new EventSummary(
                        event.getId(),
                        event.getName(),
                        event.getVenue().getId(),
                        event.getVenue().getName(),
                        event.getStartsAt(),
                        event.getTimeZone()))
                .toList();
    }
}
