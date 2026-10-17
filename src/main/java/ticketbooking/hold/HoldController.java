package ticketbooking.hold;

import ticketbooking.checkout.OrderView;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HoldController {

    private final HoldSeats holdSeats;
    private final ReleaseHold releaseHold;

    public HoldController(HoldSeats holdSeats, ReleaseHold releaseHold) {
        this.holdSeats = holdSeats;
        this.releaseHold = releaseHold;
    }

    @PostMapping("/api/events/{eventId}/holds")
    public HoldSeatsResponse hold(@PathVariable long eventId, @Valid @RequestBody HoldSeatsRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return holdSeats.hold(eventId, request.seatIds(), email);
    }

    @DeleteMapping("/api/orders/{orderId}/holds")
    public OrderView releaseAll(@PathVariable long orderId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return releaseHold.releaseAll(orderId, email);
    }

    @DeleteMapping("/api/orders/{orderId}/holds/{seatId}")
    public OrderView releaseSeat(@PathVariable long orderId, @PathVariable long seatId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return releaseHold.releaseSeat(orderId, seatId, email);
    }
}
