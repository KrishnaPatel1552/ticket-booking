package com.copart.prep.checkout;

import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.payment.Payment;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.payment.PaymentStatus;
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

    public OrderQuery(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            PaymentRepository payments) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.payments = payments;
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
                .map(claim -> new TicketView(claim.getSeat().getId(), claim.getSeat().getLabel()))
                .toList();
        PaymentView paymentView = payment == null
                ? null
                : new PaymentView(statusWire(payment.getStatus()), payment.getAmountCents());
        String promoCode = order.getPromoCode() == null ? null : order.getPromoCode().getCode();
        return new OrderView(order.getId(), order.getEvent().getId(), promoCode, paymentView, tickets);
    }

    private static String statusWire(PaymentStatus status) {
        return switch (status) {
            case PENDING -> "pending";
            case UNKNOWN -> "unknown";
            case SUCCEEDED -> "succeeded";
            case DECLINED -> "declined";
        };
    }
}
