package com.copart.prep.pricetier;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceTierController {

    private final PriceTierService priceTierService;

    public PriceTierController(PriceTierService priceTierService) {
        this.priceTierService = priceTierService;
    }

    @PostMapping("/api/admin/price-tiers")
    @ResponseStatus(HttpStatus.CREATED)
    public PriceTierView create(@Valid @RequestBody CreatePriceTierRequest request) {
        return priceTierService.create(request);
    }

    @GetMapping("/api/price-tiers")
    public List<PriceTierView> list() {
        return priceTierService.list();
    }
}
