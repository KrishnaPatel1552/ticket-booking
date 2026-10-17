package ticketbooking.event;

import java.time.Instant;
import java.util.List;

public record EventSeatMap(
        Long id,
        String name,
        Long venueId,
        String venueName,
        Instant startsAt,
        String timeZone,
        Instant earlyBirdStartsAt,
        Instant earlyBirdEndsAt,
        List<SectionSeats> sections) {
}
