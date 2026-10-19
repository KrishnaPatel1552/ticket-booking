package ticketbooking.signin;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SignedInResponse(String email, String role, String token) {

    public SignedInResponse(String email, String role) {
        this(email, role, null);
    }
}
