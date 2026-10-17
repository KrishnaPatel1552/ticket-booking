package com.copart.prep.checkout;

import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventCancellationController {

    private final CancelEvent cancelEvent;
    private final RefundScript refundScript;

    public EventCancellationController(CancelEvent cancelEvent, RefundScript refundScript) {
        this.cancelEvent = cancelEvent;
        this.refundScript = refundScript;
    }

    @PostMapping("/api/admin/events/{eventId}/cancellation")
    public void cancel(@PathVariable long eventId, @Valid @RequestBody CancelEventRequest request) {
        refundScript.scriptRefund(PaymentMockResult.fromWire(request.mock()));
        if (request.lookup() != null) {
            refundScript.scriptLookup(PaymentMockResult.fromWire(request.lookup()));
        }
        cancelEvent.submit(eventId, request.reason(), signedInEmail());
    }

    private static String signedInEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
