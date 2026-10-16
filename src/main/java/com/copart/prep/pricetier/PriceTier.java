package com.copart.prep.pricetier;

import com.copart.prep.venue.Venue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "price_tier")
public class PriceTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Venue venue;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int priceCents;

    private Integer earlyBirdPriceCents;

    protected PriceTier() {
    }

    public PriceTier(Venue venue, String name, int priceCents, Integer earlyBirdPriceCents) {
        this.venue = venue;
        this.name = name;
        this.priceCents = priceCents;
        this.earlyBirdPriceCents = earlyBirdPriceCents;
    }

    public Long getId() {
        return id;
    }

    public Venue getVenue() {
        return venue;
    }

    public String getName() {
        return name;
    }

    public int getPriceCents() {
        return priceCents;
    }

    public Integer getEarlyBirdPriceCents() {
        return earlyBirdPriceCents;
    }
}
