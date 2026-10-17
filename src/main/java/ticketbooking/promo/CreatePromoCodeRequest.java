package ticketbooking.promo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreatePromoCodeRequest(
        @NotBlank String code,
        @NotNull @PositiveOrZero Integer amountCents,
        @NotNull @PositiveOrZero Integer globalLimit,
        @NotNull @PositiveOrZero Integer perCustomerLimit,
        @NotNull Boolean exclusive) {
}
