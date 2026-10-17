package ticketbooking.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleRequest(@NotNull Instant startsAt, @NotBlank String reason) {
}
