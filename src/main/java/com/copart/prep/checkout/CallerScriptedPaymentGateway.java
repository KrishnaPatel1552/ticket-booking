package com.copart.prep.checkout;

import org.springframework.stereotype.Component;

@Component
public class CallerScriptedPaymentGateway implements PaymentGateway {

    private final PaymentScript script;

    public CallerScriptedPaymentGateway(PaymentScript script) {
        this.script = script;
    }

    @Override
    public PaymentMockResult charge(long paymentId) {
        return script.chargeOnce();
    }

    @Override
    public PaymentMockResult lookup(long paymentId) {
        return script.lookupOnce();
    }
}
