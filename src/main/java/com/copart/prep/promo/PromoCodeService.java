package com.copart.prep.promo;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PromoCodeService {

    private final PromoCodeRepository promoCodes;

    public PromoCodeService(PromoCodeRepository promoCodes) {
        this.promoCodes = promoCodes;
    }

    @Transactional
    public PromoCodeView create(CreatePromoCodeRequest request) {
        String code = request.code().trim();
        if (promoCodes.findByCode(code).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        PromoCode saved = promoCodes.save(new PromoCode(
                code,
                request.discountCents(),
                request.globalLimit(),
                request.perCustomerLimit(),
                request.exclusive()));
        return toView(saved);
    }

    private static PromoCodeView toView(PromoCode promoCode) {
        return new PromoCodeView(
                promoCode.getId(),
                promoCode.getCode(),
                promoCode.getDiscountCents(),
                promoCode.getGlobalLimit(),
                promoCode.getPerCustomerLimit(),
                promoCode.isExclusive());
    }
}
