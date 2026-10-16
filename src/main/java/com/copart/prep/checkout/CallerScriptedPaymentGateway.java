package com.copart.prep.checkout;

import org.springframework.stereotype.Component;

@Component
public class CallerScriptedPaymentGateway implements PaymentGateway {

    private final PaymentScript script;
    private final PaymentAttemptGate attemptGate;

    public CallerScriptedPaymentGateway(PaymentScript script, PaymentAttemptGate attemptGate) {
        this.script = script;
        this.attemptGate = attemptGate;
    }

    @Override
    public PaymentMockResult charge(long paymentId) {
        attemptGate.beforeCharge();
        return script.chargeOnce();
    }

    @Override
    public PaymentMockResult lookup(long paymentId) {
        return script.lookupOnce();
    }
}
