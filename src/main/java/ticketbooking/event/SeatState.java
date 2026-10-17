package ticketbooking.event;

import com.fasterxml.jackson.annotation.JsonValue;

public enum SeatState {
    FREE,
    HELD,
    BLOCKED,
    TICKET;

    @JsonValue
    public String wireValue() {
        return name().toLowerCase();
    }
}
