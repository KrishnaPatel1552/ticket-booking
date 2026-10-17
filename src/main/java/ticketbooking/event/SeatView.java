package ticketbooking.event;

public record SeatView(
        Long id,
        String label,
        int priceCents,
        Integer earlyBirdPriceCents,
        SeatState state) {
}
