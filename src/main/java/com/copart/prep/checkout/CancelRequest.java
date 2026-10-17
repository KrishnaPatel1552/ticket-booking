package com.copart.prep.checkout;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CancelRequest(@NotEmpty List<@NotNull Long> seatIds, @NotNull String mock) {
}
