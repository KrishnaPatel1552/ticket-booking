package ticketbooking.checkout;

import ticketbooking.payment.PaymentRepository;
import org.springframework.stereotype.Service;

@Service
public class ReconcilePayment {

    private final OrderQuery orders;
    private final PaymentRepository payments;
    private final PaymentGateway paymentGateway;
    private final FinishPayment finishPayment;

    public ReconcilePayment(
            OrderQuery orders,
            PaymentRepository payments,
            PaymentGateway paymentGateway,
            FinishPayment finishPayment) {
        this.orders = orders;
        this.payments = payments;
        this.paymentGateway = paymentGateway;
        this.finishPayment = finishPayment;
    }

    public OrderView lookup(long orderId, String email) {
        orders.open(orderId, email);
        payments.findNeedingReconcile(orderId).map(payment -> payment.getId()).ifPresent(paymentId -> {
            PaymentMockResult result = paymentGateway.lookup(paymentId);
            finishPayment.applyLookup(paymentId, result);
        });
        return orders.open(orderId, email);
    }
}
