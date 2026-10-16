package com.copart.prep.checkout;

public record TicketView(
        long id,
        long seatId,
        String label,
        Integer startingPriceCents,
        Integer groupShareCents,
        Integer promoShareCents,
        Integer feeShareCents) {
}
