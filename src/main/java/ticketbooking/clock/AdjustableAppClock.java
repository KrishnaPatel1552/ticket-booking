package ticketbooking.clock;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class AdjustableAppClock implements AppClock {

    private final AtomicReference<Instant> current = new AtomicReference<>(Instant.EPOCH);

    @Override
    public Instant now() {
        return current.get();
    }

    public void moveTo(Instant instant) {
        current.set(instant);
    }
}
