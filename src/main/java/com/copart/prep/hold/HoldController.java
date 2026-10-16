package com.copart.prep.hold;

import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HoldController {

    private final HoldSeats holdSeats;

    public HoldController(HoldSeats holdSeats) {
        this.holdSeats = holdSeats;
    }

    @PostMapping("/api/events/{eventId}/holds")
    public HoldSeatsResponse hold(@PathVariable long eventId, @Valid @RequestBody HoldSeatsRequest request) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return holdSeats.hold(eventId, request.seatIds(), email);
    }
}
