package com.copart.prep.checkout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CancelEventRequest(@NotBlank String reason, @NotNull String mock, String lookup) {
}
