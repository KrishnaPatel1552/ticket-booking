package com.copart.prep.promo;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ContinuingPromoCodeLockGate implements PromoCodeLockGate {

    @Override
    public void afterPromoCodeLock() {
    }
}
