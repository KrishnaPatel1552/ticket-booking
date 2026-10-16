package com.copart.prep.checkout;

import java.util.List;

public record OrderView(long id, long eventId, PaymentView payment, List<TicketView> tickets) {
}
