package com.copart.prep.pricetier;

import com.copart.prep.venue.Venue;
import com.copart.prep.venue.VenueRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PriceTierService {

    private final PriceTierRepository priceTiers;
    private final VenueRepository venues;

    public PriceTierService(PriceTierRepository priceTiers, VenueRepository venues) {
        this.priceTiers = priceTiers;
        this.venues = venues;
    }

    @Transactional
    public PriceTierView create(CreatePriceTierRequest request) {
        Venue venue = venues.findById(request.venueId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        PriceTier saved = priceTiers.save(new PriceTier(
                venue,
                request.name(),
                request.priceCents(),
                request.earlyBirdPriceCents()));
        return toView(saved);
    }

    @Transactional(readOnly = true)
    public List<PriceTierView> list() {
        return priceTiers.findAllByOrderByIdAsc().stream().map(this::toView).toList();
    }

    private PriceTierView toView(PriceTier priceTier) {
        return new PriceTierView(
                priceTier.getId(),
                priceTier.getName(),
                priceTier.getVenue().getId(),
                priceTier.getPriceCents(),
                priceTier.getEarlyBirdPriceCents());
    }
}
