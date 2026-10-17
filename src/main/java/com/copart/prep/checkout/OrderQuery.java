package com.copart.prep.checkout;

import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.Payment;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.payment.PaymentStatus;
import com.copart.prep.refund.RefundRepository;
import com.copart.prep.refund.RefundStatus;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderQuery {

    private final AccountRepository accounts;
    private final CustomerOrderRepository orders;
    private final EventSeatRepository eventSeats;
    private final PaymentRepository payments;
    private final RefundRepository refunds;

    public OrderQuery(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            PaymentRepository payments,
            RefundRepository refunds) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.payments = payments;
        this.refunds = refunds;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public OrderView open(long orderId, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        Payment payment = payments.findByOrder_Id(orderId).orElse(null);
        List<TicketView> tickets = eventSeats.findByOrderIdAndActiveTrueAndKind(orderId, EventSeatKind.TICKET).stream()
                .map(claim -> new TicketView(
                        claim.getId(),
                        claim.getSeat().getId(),
                        claim.getSeat().getLabel(),
                        claim.getStartingPriceCents(),
                        claim.getGroupShareCents(),
                        claim.getPromoShareCents(),
                        claim.getFeeShareCents()))
                .toList();
        PaymentView paymentView = payment == null
                ? null
                : new PaymentView(statusWire(payment.getStatus()), payment.getAmountCents());
        List<RefundView> refundViews = refunds.findByOrder_IdOrderByIdAsc(orderId).stream()
                .map(refund -> new RefundView(statusWire(refund.getStatus()), refund.getAmountCents()))
                .toList();
        List<HoldView> holds = eventSeats.findByOrderIdAndActiveTrueAndKind(orderId, EventSeatKind.HOLD).stream()
                .map(claim -> new HoldView(claim.getSeat().getId(), claim.getSeat().getLabel(), claim.getHeldUntil()))
                .toList();
        String promoCode = order.getPromoCode() == null ? null : order.getPromoCode().getCode();
        return new OrderView(
                order.getId(),
                order.getEvent().getId(),
                promoCode,
                paymentView,
                tickets,
                refundViews,
                holds);
    }

    private static String statusWire(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case UNKNOWN -> "unknown";
            case SUCCEEDED -> "succeeded";
            case DECLINED -> "declined";
        };
    }

    private static String statusWire(RefundStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case UNKNOWN -> "unknown";
            case SUCCEEDED -> "succeeded";
            case FAILED -> "failed";
        };
    }
}
