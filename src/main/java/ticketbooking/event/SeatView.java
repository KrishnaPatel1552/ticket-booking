package ticketbooking.event;

import java.time.Instant;

public record SeatView(
        Long id,
        String label,
        int priceCents,
        Integer earlyBirdPriceCents,
        SeatState state,
        boolean yours,
        Instant heldUntil) {
}
