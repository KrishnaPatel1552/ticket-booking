package com.copart.prep.checkout;

import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CheckoutController {

    private final Checkout checkout;
    private final OrderQuery orders;
    private final ReconcilePayment reconcilePayment;
    private final PaymentScript script;

    public CheckoutController(
            Checkout checkout,
            OrderQuery orders,
            ReconcilePayment reconcilePayment,
            PaymentScript script) {
        this.checkout = checkout;
        this.orders = orders;
        this.reconcilePayment = reconcilePayment;
        this.script = script;
    }

    @PostMapping("/api/orders/{orderId}/checkout")
    public OrderView checkout(@PathVariable long orderId, @Valid @RequestBody CheckoutRequest request) {
        script.scriptCharge(PaymentMockResult.fromWire(request.mock()));
        return checkout.submit(orderId, request.seatIds(), signedInEmail());
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderView open(@PathVariable long orderId, @RequestParam(required = false) String lookup) {
        String email = signedInEmail();
        if (lookup == null) {
            return orders.open(orderId, email);
        }
        script.scriptLookup(PaymentMockResult.fromWire(lookup));
        return reconcilePayment.lookup(orderId, email);
    }

    private static String signedInEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
