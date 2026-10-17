package com.copart.prep.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExceptionalRefundRequest(@NotNull Integer amountCents, @NotBlank String reason, @NotNull String mock) {
}
