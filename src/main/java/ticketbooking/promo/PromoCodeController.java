package ticketbooking.promo;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PromoCodeController {

    private final PromoCodeService promoCodeService;

    public PromoCodeController(PromoCodeService promoCodeService) {
        this.promoCodeService = promoCodeService;
    }

    @PostMapping("/api/admin/promo-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public PromoCodeView create(@Valid @RequestBody CreatePromoCodeRequest request) {
        return promoCodeService.create(request);
    }
}
