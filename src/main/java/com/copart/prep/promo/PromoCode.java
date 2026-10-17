package com.copart.prep.promo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "promo_code")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private int amountCents;

    @Column(nullable = false)
    private int globalLimit;

    @Column(nullable = false)
    private int perCustomerLimit;

    @Column(nullable = false)
    private boolean exclusive;

    protected PromoCode() {
    }

    public PromoCode(String code, int amountCents, int globalLimit, int perCustomerLimit, boolean exclusive) {
        this.code = code;
        this.amountCents = amountCents;
        this.globalLimit = globalLimit;
        this.perCustomerLimit = perCustomerLimit;
        this.exclusive = exclusive;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public int getGlobalLimit() {
        return globalLimit;
    }

    public int getPerCustomerLimit() {
        return perCustomerLimit;
    }

    public boolean isExclusive() {
        return exclusive;
    }
}
