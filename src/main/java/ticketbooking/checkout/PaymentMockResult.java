package ticketbooking.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public enum PaymentMockResult {
    SUCCESS,
    DECLINE,
    TIMEOUT,
    UNKNOWN;

    public static PaymentMockResult fromWire(String wire) {
        return switch (wire) {
            case "success" -> SUCCESS;
            case "decline" -> DECLINE;
            case "timeout" -> TIMEOUT;
            case "unknown" -> UNKNOWN;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        };
    }
}
