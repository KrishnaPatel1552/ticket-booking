package com.copart.prep.checkout;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CancelTickets {

    private final RecordPendingRefund recordPendingRefund;
    private final RefundGateway refundGateway;
    private final FinishRefund finishRefund;
    private final OrderQuery orders;

    public CancelTickets(
            RecordPendingRefund recordPendingRefund,
            RefundGateway refundGateway,
            FinishRefund finishRefund,
            OrderQuery orders) {
        this.recordPendingRefund = recordPendingRefund;
        this.refundGateway = refundGateway;
        this.finishRefund = finishRefund;
        this.orders = orders;
    }

    public OrderView submit(long orderId, List<Long> seatIds, String email) {
        long refundId = recordPendingRefund.record(orderId, seatIds, email);
        PaymentMockResult result = refundGateway.refund(refundId);
        finishRefund.apply(refundId, result);
        return orders.open(orderId, email);
    }
}
