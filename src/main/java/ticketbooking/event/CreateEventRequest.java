package ticketbooking.event;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record CreateEventRequest(
        @NotBlank String name,
        @NotNull Long venueId,
        @NotNull Instant startsAt,
        @NotBlank String timeZone,
        Instant earlyBirdStartsAt,
        Instant earlyBirdEndsAt) {
}
