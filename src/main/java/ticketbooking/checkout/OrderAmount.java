package ticketbooking.checkout;

import java.util.List;

public final class OrderAmount {

    private OrderAmount() {
    }

    public static int cents(int serviceFeeCents, List<Integer> priceTierCents) {
        int amount = serviceFeeCents;
        for (int priceCents : priceTierCents) {
            amount += priceCents;
        }
        return amount;
    }
}
