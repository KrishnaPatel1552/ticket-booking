package ticketbooking.promo;

import ticketbooking.checkout.OrderQuery;
import ticketbooking.checkout.OrderView;
import ticketbooking.hold.CustomerOrder;
import ticketbooking.hold.CustomerOrderRepository;
import ticketbooking.payment.PaymentRepository;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
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
