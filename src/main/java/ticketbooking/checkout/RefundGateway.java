package ticketbooking.checkout;

public interface RefundGateway {

    PaymentMockResult refund(long refundId);

    PaymentMockResult lookup(long refundId);
}
