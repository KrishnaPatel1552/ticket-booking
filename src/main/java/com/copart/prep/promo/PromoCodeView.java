package com.copart.prep.promo;

public record PromoCodeView(
        Long id,
        String code,
        int amountCents,
        int globalLimit,
        int perCustomerLimit,
        boolean exclusive) {
}
