package com.copart.prep.hold;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class ContinuingHoldGate implements HoldGate {

    @Override
    public void afterCustomerEventLock() {
    }

    @Override
    public void beforeWritingSeats() {
    }

    @Override
    public void afterSeatLock() {
    }
}
