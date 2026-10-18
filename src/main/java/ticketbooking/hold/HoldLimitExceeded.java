package ticketbooking.hold;

public class HoldLimitExceeded extends RuntimeException {

    private final String reason;

    public HoldLimitExceeded(String reason) {
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
