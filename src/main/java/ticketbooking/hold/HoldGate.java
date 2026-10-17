package ticketbooking.hold;

public interface HoldGate {

    void afterCustomerEventLock();

    void beforeWritingSeats();

    void afterSeatLock();
}
