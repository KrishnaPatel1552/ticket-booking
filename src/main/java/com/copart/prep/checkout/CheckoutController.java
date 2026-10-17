package com.copart.prep.checkout;

import com.copart.prep.promo.AttachPromoCode;
import com.copart.prep.promo.AttachPromoCodeRequest;
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
    private final AttachPromoCode attachPromoCode;
    private final CancelTickets cancelTickets;
    private final ReconcileRefund reconcileRefund;
    private final RefundScript refundScript;

    public CheckoutController(
            Checkout checkout,
            OrderQuery orders,
            ReconcilePayment reconcilePayment,
            PaymentScript script,
            AttachPromoCode attachPromoCode,
            CancelTickets cancelTickets,
            ReconcileRefund reconcileRefund,
            RefundScript refundScript) {
        this.checkout = checkout;
        this.orders = orders;
        this.reconcilePayment = reconcilePayment;
        this.script = script;
        this.attachPromoCode = attachPromoCode;
        this.cancelTickets = cancelTickets;
        this.reconcileRefund = reconcileRefund;
        this.refundScript = refundScript;
    }

    @PostMapping("/api/orders/{orderId}/promo-code")
    public OrderView attachPromo(@PathVariable long orderId, @Valid @RequestBody AttachPromoCodeRequest request) {
        return attachPromoCode.attach(orderId, request.code(), signedInEmail());
    }

    @PostMapping("/api/orders/{orderId}/checkout")
    public OrderView checkout(@PathVariable long orderId, @Valid @RequestBody CheckoutRequest request) {
        script.scriptCharge(PaymentMockResult.fromWire(request.mock()));
        return checkout.submit(orderId, request.seatIds(), signedInEmail());
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    public OrderView cancel(@PathVariable long orderId, @Valid @RequestBody CancelRequest request) {
        refundScript.scriptRefund(PaymentMockResult.fromWire(request.mock()));
        return cancelTickets.submit(orderId, request.seatIds(), signedInEmail());
    }

    @GetMapping("/api/orders/{orderId}")
    public OrderView open(
            @PathVariable long orderId,
            @RequestParam(required = false) String lookup,
            @RequestParam(required = false) String refundLookup) {
        String email = signedInEmail();
        if (lookup == null && refundLookup == null) {
            return orders.open(orderId, email);
        }
        if (lookup != null) {
            script.scriptLookup(PaymentMockResult.fromWire(lookup));
        }
        if (refundLookup != null) {
            refundScript.scriptLookup(PaymentMockResult.fromWire(refundLookup));
        }
        OrderView view = lookup != null ? reconcilePayment.lookup(orderId, email) : orders.open(orderId, email);
        if (refundLookup != null) {
            view = reconcileRefund.lookup(orderId, email);
        }
        return view;
    }

    @PostMapping("/api/orders/{orderId}/reconcile")
    public OrderView reconcile(@PathVariable long orderId, @Valid @RequestBody ReconcileRequest request) {
        script.scriptLookup(PaymentMockResult.fromWire(request.lookup()));
        return reconcilePayment.lookup(orderId, signedInEmail());
    }

    @PostMapping("/api/orders/{orderId}/refunds/reconcile")
    public OrderView reconcileRefund(@PathVariable long orderId, @Valid @RequestBody ReconcileRequest request) {
        refundScript.scriptLookup(PaymentMockResult.fromWire(request.lookup()));
        return reconcileRefund.lookup(orderId, signedInEmail());
    }

    private static String signedInEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
