package ticketbooking.checkout;

import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RescheduleController {

    private final RecordReschedule recordReschedule;

    public RescheduleController(RecordReschedule recordReschedule) {
        this.recordReschedule = recordReschedule;
    }

    @PostMapping("/api/admin/events/{eventId}/reschedule")
    public void reschedule(@PathVariable long eventId, @Valid @RequestBody RescheduleRequest request) {
        recordReschedule.record(eventId, request.startsAt(), request.reason(), signedInEmail());
    }

    private static String signedInEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
