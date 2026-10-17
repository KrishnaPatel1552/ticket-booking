package com.copart.prep.checkout;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExceptionalRefundController {

    private final IssueExceptionalRefund issueExceptionalRefund;
    private final RefundScript refundScript;

    public ExceptionalRefundController(IssueExceptionalRefund issueExceptionalRefund, RefundScript refundScript) {
        this.issueExceptionalRefund = issueExceptionalRefund;
        this.refundScript = refundScript;
    }

    @PostMapping("/api/admin/orders/{orderId}/exceptional-refunds")
    public OrderView issue(@PathVariable long orderId, @RequestBody ExceptionalRefundRequest request) {
        refundScript.scriptRefund(PaymentMockResult.fromWire(request.mock()));
        return issueExceptionalRefund.submit(orderId, request.amountCents(), request.reason(), signedInEmail());
    }

    private static String signedInEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }
}
