package ticketbooking.pricetier;

public record PriceTierView(
        Long id,
        String name,
        Long venueId,
        int priceCents,
        Integer earlyBirdPriceCents) {
}
