package com.copart.prep.checkout;

import org.springframework.stereotype.Service;

@Service
public class IssueExceptionalRefund {

    private final RecordPendingExceptionalRefund recordPendingExceptionalRefund;
    private final RefundGateway refundGateway;
    private final FinishRefund finishRefund;
    private final OrderQuery orders;

    public IssueExceptionalRefund(
            RecordPendingExceptionalRefund recordPendingExceptionalRefund,
            RefundGateway refundGateway,
            FinishRefund finishRefund,
            OrderQuery orders) {
        this.recordPendingExceptionalRefund = recordPendingExceptionalRefund;
        this.refundGateway = refundGateway;
        this.finishRefund = finishRefund;
        this.orders = orders;
    }

    public OrderView submit(long orderId, int amountCents, String reason, String adminEmail) {
        RecordedExceptionalRefund recorded = recordPendingExceptionalRefund.record(
                orderId, amountCents, reason, adminEmail);
        PaymentMockResult result = refundGateway.refund(recorded.refundId());
        finishRefund.apply(recorded.refundId(), result);
        return orders.open(orderId, recorded.customerEmail());
    }
}
