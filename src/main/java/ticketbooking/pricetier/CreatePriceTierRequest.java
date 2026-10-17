package ticketbooking.pricetier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreatePriceTierRequest(
        @NotBlank String name,
        @NotNull Long venueId,
        @NotNull @PositiveOrZero Integer priceCents,
        @PositiveOrZero Integer earlyBirdPriceCents) {
}
