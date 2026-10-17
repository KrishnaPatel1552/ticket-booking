package com.copart.prep.sales;

public record SalesView(
        int free,
        int held,
        int blocked,
        int tickets,
        int ticketsSold,
        int totalPaidCents) {
}
