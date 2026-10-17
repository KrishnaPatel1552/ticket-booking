package ticketbooking.checkout;

import ticketbooking.refund.RefundRepository;
import org.springframework.stereotype.Service;

@Service
public class ReconcileRefund {

    private final OrderQuery orders;
    private final RefundRepository refunds;
    private final RefundGateway refundGateway;
    private final FinishRefund finishRefund;

    public ReconcileRefund(
            OrderQuery orders,
            RefundRepository refunds,
            RefundGateway refundGateway,
            FinishRefund finishRefund) {
        this.orders = orders;
        this.refunds = refunds;
        this.refundGateway = refundGateway;
        this.finishRefund = finishRefund;
    }

    public OrderView lookup(long orderId, String email) {
        orders.open(orderId, email);
        refunds.findNeedingLookup(orderId).stream().findFirst().map(refund -> refund.getId()).ifPresent(refundId -> {
            PaymentMockResult result = refundGateway.lookup(refundId);
            finishRefund.applyLookup(refundId, result);
        });
        return orders.open(orderId, email);
    }
}
