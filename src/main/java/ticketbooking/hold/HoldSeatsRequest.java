package ticketbooking.hold;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record HoldSeatsRequest(@NotEmpty List<@NotNull Long> seatIds) {
}
