package ticketbooking.checkout;

import java.time.Duration;
import java.time.Instant;

final class RefundBand {

    private RefundBand() {
    }

    static int cents(int net, Instant now, Instant startsAt) {
        Duration until = Duration.between(now, startsAt);
        if (until.compareTo(Duration.ofHours(168)) >= 0) {
            return net;
        }
        if (until.compareTo(Duration.ofHours(48)) >= 0) {
            return (net + 1) / 2;
        }
        return 0;
    }
}
