package com.copart.prep.checkout;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ContinuingPaymentAttemptGate implements PaymentAttemptGate {

    @Override
    public void beforePay() {
    }
}
