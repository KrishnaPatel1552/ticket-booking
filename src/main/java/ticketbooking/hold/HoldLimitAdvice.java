package ticketbooking.hold;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class HoldLimitAdvice {

    @ExceptionHandler(HoldLimitExceeded.class)
    public ResponseEntity<Map<String, String>> conflict(HoldLimitExceeded exceeded) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("reason", exceeded.reason()));
    }
}
