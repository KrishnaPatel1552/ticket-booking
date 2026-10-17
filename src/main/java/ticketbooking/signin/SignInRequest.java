package ticketbooking.signin;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(@NotBlank String email, @NotBlank String password) {
}
