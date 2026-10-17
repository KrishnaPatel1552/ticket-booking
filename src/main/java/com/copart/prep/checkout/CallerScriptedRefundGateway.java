package com.copart.prep.checkout;

import org.springframework.stereotype.Component;

@Component
public class CallerScriptedRefundGateway implements RefundGateway {

    private final RefundScript script;
    private final RefundAttemptGate attemptGate;

    public CallerScriptedRefundGateway(RefundScript script, RefundAttemptGate attemptGate) {
        this.script = script;
        this.attemptGate = attemptGate;
    }

    @Override
    public PaymentMockResult refund(long refundId) {
        attemptGate.beforeRefund();
        return script.refundOnce();
    }

    @Override
    public PaymentMockResult lookup(long refundId) {
        return script.lookupOnce();
    }
}
