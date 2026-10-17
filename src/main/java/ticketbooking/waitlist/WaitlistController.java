package ticketbooking.waitlist;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaitlistController {

    private final JoinWaitlist joinWaitlist;

    public WaitlistController(JoinWaitlist joinWaitlist) {
        this.joinWaitlist = joinWaitlist;
    }

    @PostMapping("/api/events/{eventId}/waitlist")
    public WaitlistView join(@PathVariable long eventId) {
        return joinWaitlist.join(eventId, signedInEmail());
    }

    @GetMapping("/api/events/{eventId}/waitlist")
    public WaitlistView read(@PathVariable long eventId) {
        return joinWaitlist.read(eventId, signedInEmail());
    }

    private static String signedInEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
