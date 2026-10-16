package com.copart.prep.saleterms;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SaleTermsController {

    private final SaleTermsService saleTermsService;

    public SaleTermsController(SaleTermsService saleTermsService) {
        this.saleTermsService = saleTermsService;
    }

    @GetMapping("/api/sale-terms")
    public SaleTermsView read() {
        return saleTermsService.read();
    }
}
