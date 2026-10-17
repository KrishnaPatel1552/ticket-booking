package ticketbooking.checkout;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ContinuingRefundAttemptGate implements RefundAttemptGate {

    @Override
    public void beforeRefund() {
    }
}
