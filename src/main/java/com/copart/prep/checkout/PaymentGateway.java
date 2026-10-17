package com.copart.prep.checkout;

public interface PaymentGateway {

    PaymentMockResult pay(long paymentId);

    PaymentMockResult lookup(long paymentId);
}
