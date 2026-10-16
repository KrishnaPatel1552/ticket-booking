package com.copart.prep.hold;

import com.copart.prep.event.Event;
import com.copart.prep.promo.PromoCode;
import com.copart.prep.signin.Account;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Account customer;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Event event;

    @Column(nullable = false)
    private Instant openedAt;

    @Column(nullable = false)
    private boolean openForHolds;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promo_code_id")
    private PromoCode promoCode;

    protected CustomerOrder() {
    }

    public CustomerOrder(Account customer, Event event, Instant openedAt) {
        this.customer = customer;
        this.event = event;
        this.openedAt = openedAt;
        this.openForHolds = true;
    }

    public Long getId() {
        return id;
    }

    public Account getCustomer() {
        return customer;
    }

    public Event getEvent() {
        return event;
    }

    public Instant getOpenedAt() {
        return openedAt;
    }

    public boolean isOpenForHolds() {
        return openForHolds;
    }

    public void closeForHolds() {
        this.openForHolds = false;
    }

    public PromoCode getPromoCode() {
        return promoCode;
    }

    public void attachPromoCode(PromoCode promoCode) {
        this.promoCode = promoCode;
    }
}
