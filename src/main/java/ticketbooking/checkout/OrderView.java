package ticketbooking.checkout;

import java.util.List;

public record OrderView(
        long id,
        long eventId,
        String promoCode,
        PaymentView payment,
        List<TicketView> tickets,
        List<RefundView> refunds,
        List<HoldView> holds) {
}
