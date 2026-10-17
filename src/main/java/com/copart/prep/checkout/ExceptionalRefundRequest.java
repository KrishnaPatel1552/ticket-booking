package com.copart.prep.checkout;

public record ExceptionalRefundRequest(int amountCents, String reason, String mock) {
}
