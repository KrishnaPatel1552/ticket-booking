package com.copart.prep.promo;

public record PromoCodeView(
        Long id,
        String code,
        int discountCents,
        int globalLimit,
        int perCustomerLimit,
        boolean exclusive) {
}
