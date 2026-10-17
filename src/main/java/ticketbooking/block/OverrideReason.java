package ticketbooking.block;

import jakarta.validation.constraints.NotBlank;

public record OverrideReason(@NotBlank String reason) {
}
