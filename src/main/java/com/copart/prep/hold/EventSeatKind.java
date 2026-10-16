package com.copart.prep.hold;

import com.copart.prep.event.SeatState;

public enum EventSeatKind {
    HOLD,
    TICKET;

    public SeatState seatState() {
        return switch (this) {
            case HOLD -> SeatState.HELD;
            case TICKET -> SeatState.TICKET;
        };
    }
}
