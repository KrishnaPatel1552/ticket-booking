package ticketbooking.event;

import java.time.Instant;

public record EventSummary(
        Long id,
        String name,
        Long venueId,
        String venueName,
        Instant startsAt,
        String timeZone) {
}
