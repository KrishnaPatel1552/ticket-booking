package ticketbooking.checkout;

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
    public PaymentMockResult pay(long paymentId) {
        attemptGate.beforePay();
        return script.payOnce();
    }

    @Override
    public PaymentMockResult lookup(long paymentId) {
        return script.lookupOnce();
    }
}
