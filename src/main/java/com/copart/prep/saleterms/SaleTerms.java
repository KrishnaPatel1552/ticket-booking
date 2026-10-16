package com.copart.prep.saleterms;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sale_terms")
public class SaleTerms {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private int groupRateThreshold;

    @Column(nullable = false)
    private int groupRateAmountCents;

    @Column(nullable = false)
    private int serviceFeeCents;

    protected SaleTerms() {
    }

    public SaleTerms(int groupRateThreshold, int groupRateAmountCents, int serviceFeeCents) {
        this.groupRateThreshold = groupRateThreshold;
        this.groupRateAmountCents = groupRateAmountCents;
        this.serviceFeeCents = serviceFeeCents;
    }

    public int getGroupRateThreshold() {
        return groupRateThreshold;
    }

    public int getGroupRateAmountCents() {
        return groupRateAmountCents;
    }

    public int getServiceFeeCents() {
        return serviceFeeCents;
    }
}
