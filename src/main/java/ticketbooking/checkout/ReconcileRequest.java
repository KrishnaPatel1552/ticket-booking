package ticketbooking.checkout;

import jakarta.validation.constraints.NotNull;

public record ReconcileRequest(@NotNull String lookup) {
}
