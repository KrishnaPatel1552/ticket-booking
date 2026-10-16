package com.copart.prep.promo;

import com.copart.prep.checkout.OrderQuery;
import com.copart.prep.checkout.OrderView;
import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.payment.PaymentRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AttachPromoCode {

    private final AccountRepository accounts;
    private final CustomerOrderRepository orders;
    private final PromoCodeRepository promoCodes;
    private final PaymentRepository payments;
    private final OrderQuery orderQuery;

    public AttachPromoCode(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            PromoCodeRepository promoCodes,
            PaymentRepository payments,
            OrderQuery orderQuery) {
        this.accounts = accounts;
        this.orders = orders;
        this.promoCodes = promoCodes;
        this.payments = payments;
        this.orderQuery = orderQuery;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderView attach(long orderId, String code, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (payments.existsForOrder(orderId) || order.getPromoCode() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        PromoCode promoCode = promoCodes.findByCode(code.trim())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        order.attachPromoCode(promoCode);
        return orderQuery.open(orderId, email);
    }
}
