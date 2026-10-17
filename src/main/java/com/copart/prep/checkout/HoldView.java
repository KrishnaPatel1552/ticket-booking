package com.copart.prep.checkout;

import java.time.Instant;

public record HoldView(long seatId, String label, Instant heldUntil) {
}
