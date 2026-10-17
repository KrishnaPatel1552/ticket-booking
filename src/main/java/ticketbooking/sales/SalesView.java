package ticketbooking.sales;

public record SalesView(
        int free,
        int held,
        int blocked,
        int tickets,
        int ticketsSold,
        int totalPaidCents) {
}
