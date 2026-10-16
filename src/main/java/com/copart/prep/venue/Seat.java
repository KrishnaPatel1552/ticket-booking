package com.copart.prep.venue;

import com.copart.prep.pricetier.PriceTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "seat")
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Section section;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private PriceTier priceTier;

    @Column(nullable = false)
    private String label;

    protected Seat() {
    }

    public Seat(Section section, PriceTier priceTier, String label) {
        this.section = section;
        this.priceTier = priceTier;
        this.label = label;
    }

    public Long getId() {
        return id;
    }

    public Section getSection() {
        return section;
    }

    public PriceTier getPriceTier() {
        return priceTier;
    }

    public String getLabel() {
        return label;
    }
}
