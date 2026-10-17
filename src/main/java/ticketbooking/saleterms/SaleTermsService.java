package ticketbooking.saleterms;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SaleTermsService {

    private final SaleTermsRepository saleTerms;

    public SaleTermsService(SaleTermsRepository saleTerms) {
        this.saleTerms = saleTerms;
    }

    @Transactional(readOnly = true)
    public SaleTermsView read() {
        SaleTerms terms = saleTerms.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return new SaleTermsView(
                terms.getGroupRateThreshold(),
                terms.getGroupRateAmountCents(),
                terms.getServiceFeeCents());
    }
}
