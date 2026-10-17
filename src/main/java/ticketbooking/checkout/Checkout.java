package ticketbooking.checkout;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class Checkout {

    private final RecordPendingPayment recordPendingPayment;
    private final PaymentGateway paymentGateway;
    private final FinishPayment finishPayment;
    private final OrderQuery orders;

    public Checkout(
            RecordPendingPayment recordPendingPayment,
            PaymentGateway paymentGateway,
            FinishPayment finishPayment,
            OrderQuery orders) {
        this.recordPendingPayment = recordPendingPayment;
        this.paymentGateway = paymentGateway;
        this.finishPayment = finishPayment;
        this.orders = orders;
    }

    public OrderView submit(long orderId, List<Long> seatIds, String email) {
        long paymentId = recordPendingPayment.record(orderId, seatIds, email);
        PaymentMockResult result = paymentGateway.pay(paymentId);
        finishPayment.apply(paymentId, seatIds, result);
        return orders.open(orderId, email);
    }
}
