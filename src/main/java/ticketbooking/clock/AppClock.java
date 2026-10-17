package ticketbooking.clock;

import java.time.Instant;

public interface AppClock {

    Instant now();
}
