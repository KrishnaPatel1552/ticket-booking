package com.copart.prep.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequestScope
public class PaymentScript {

    private PaymentMockResult charge;
    private PaymentMockResult lookup;
    private boolean charged;

    public void scriptCharge(PaymentMockResult result) {
        this.charge = result;
    }

    public void scriptLookup(PaymentMockResult result) {
        this.lookup = result;
    }

    public PaymentMockResult chargeOnce() {
        if (charged || charge == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        charged = true;
        return charge;
    }

    public PaymentMockResult lookupOnce() {
        if (lookup == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return lookup;
    }
}
