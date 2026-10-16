package com.copart.prep.checkout;

public interface PaymentGateway {

    PaymentMockResult charge(long paymentId);

    PaymentMockResult lookup(long paymentId);
}
