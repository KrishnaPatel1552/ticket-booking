package ticketbooking.checkout;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CheckoutRequest(@NotEmpty List<@NotNull Long> seatIds, @NotNull String mock) {
}
