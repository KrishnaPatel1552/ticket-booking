package ticketbooking.promo;

import jakarta.validation.constraints.NotBlank;

public record AttachPromoCodeRequest(@NotBlank String code) {
}
