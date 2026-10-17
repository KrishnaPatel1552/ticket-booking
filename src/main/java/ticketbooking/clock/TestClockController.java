package ticketbooking.clock;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("test")
public class TestClockController {

    private final AppClock clock;
    private final AdjustableAppClock adjustableClock;

    public TestClockController(AppClock clock, AdjustableAppClock adjustableClock) {
        this.clock = clock;
        this.adjustableClock = adjustableClock;
    }

    @GetMapping("/api/test/clock")
    public ClockResponse read() {
        return new ClockResponse(clock.now());
    }

    @PostMapping("/api/test/clock")
    public ClockResponse move(@Valid @RequestBody MoveClockRequest request) {
        adjustableClock.moveTo(request.instant());
        return new ClockResponse(clock.now());
    }
}
