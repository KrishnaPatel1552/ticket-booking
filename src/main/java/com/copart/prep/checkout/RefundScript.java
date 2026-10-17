package com.copart.prep.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequestScope
public class RefundScript {

    private PaymentMockResult refund;
    private PaymentMockResult lookup;
    private boolean refunded;

    public void scriptRefund(PaymentMockResult result) {
        this.refund = result;
    }

    public void scriptLookup(PaymentMockResult result) {
        this.lookup = result;
    }

    public PaymentMockResult refundOnce() {
        if (refunded || refund == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        refunded = true;
        return refund;
    }

    public PaymentMockResult lookupOnce() {
        if (lookup == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return lookup;
    }
}
