package com.copart.prep.checkout;

public interface RefundGateway {

    PaymentMockResult refund(long refundId);

    PaymentMockResult lookup(long refundId);
}
