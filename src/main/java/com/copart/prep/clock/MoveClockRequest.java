package com.copart.prep.clock;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record MoveClockRequest(@NotNull Instant instant) {
}
