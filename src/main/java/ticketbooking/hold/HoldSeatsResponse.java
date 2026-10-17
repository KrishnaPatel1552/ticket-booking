package ticketbooking.hold;

import java.time.Instant;
import java.util.List;

public record HoldSeatsResponse(long orderId, List<Long> seatIds, Instant heldUntil) {
}
