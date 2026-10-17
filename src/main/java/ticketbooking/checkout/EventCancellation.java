package ticketbooking.checkout;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class EventCancellation {

    private final RecordEventCancellation recordEventCancellation;
    private final RecordEventCancellationRefund recordEventCancellationRefund;
    private final RefundGateway refundGateway;
    private final FinishRefund finishRefund;

    public EventCancellation(
            RecordEventCancellation recordEventCancellation,
            RecordEventCancellationRefund recordEventCancellationRefund,
            RefundGateway refundGateway,
            FinishRefund finishRefund) {
        this.recordEventCancellation = recordEventCancellation;
        this.recordEventCancellationRefund = recordEventCancellationRefund;
        this.refundGateway = refundGateway;
        this.finishRefund = finishRefund;
    }

    public void submit(long eventId, String reason, String adminEmail) {
        List<Long> openCustomerRefunds = recordEventCancellation.record(eventId, reason, adminEmail);
        for (Long refundId : openCustomerRefunds) {
            PaymentMockResult lookup = refundGateway.lookup(refundId);
            finishRefund.applyLookup(refundId, lookup);
        }
        for (Long ticketId : recordEventCancellationRefund.ticketsNeedingRefund(eventId)) {
            long refundId = recordEventCancellationRefund.record(ticketId);
            PaymentMockResult result = refundGateway.refund(refundId);
            finishRefund.apply(refundId, result);
        }
    }
}
