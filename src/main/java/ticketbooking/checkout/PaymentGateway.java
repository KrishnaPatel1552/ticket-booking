package ticketbooking.checkout;

public interface PaymentGateway {

    PaymentMockResult pay(long paymentId);

    PaymentMockResult lookup(long paymentId);
}
