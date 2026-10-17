package ticketbooking.checkout;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequestScope
public class PaymentScript {

    private PaymentMockResult pay;
    private PaymentMockResult lookup;
    private boolean paid;

    public void scriptPay(PaymentMockResult result) {
        this.pay = result;
    }

    public void scriptLookup(PaymentMockResult result) {
        this.lookup = result;
    }

    public PaymentMockResult payOnce() {
        if (paid || pay == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        paid = true;
        return pay;
    }

    public PaymentMockResult lookupOnce() {
        if (lookup == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        return lookup;
    }
}
