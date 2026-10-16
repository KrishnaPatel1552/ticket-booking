package com.copart.prep.hold;

public interface HoldGate {

    void afterCustomerEventLock();

    void beforeWritingSeats();
}
