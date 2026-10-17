package ticketbooking.refund;

import ticketbooking.hold.CustomerOrder;
import ticketbooking.hold.EventSeat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "refund")
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    @Column(nullable = false)
    private int amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundKind kind;

    @ManyToMany
    @JoinTable(
            name = "refund_ticket",
            joinColumns = @JoinColumn(name = "refund_id"),
            inverseJoinColumns = @JoinColumn(name = "event_seat_id"))
    private List<EventSeat> tickets = new ArrayList<>();

    protected Refund() {
    }

    public Refund(
            CustomerOrder order,
            int amountCents,
            RefundStatus status,
            Instant createdAt,
            RefundKind kind) {
        this.order = order;
        this.amountCents = amountCents;
        this.status = status;
        this.createdAt = createdAt;
        this.kind = kind;
    }

    public Long getId() {
        return id;
    }

    public CustomerOrder getOrder() {
        return order;
    }

    public int getAmountCents() {
        return amountCents;
    }

    public RefundStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public RefundKind getKind() {
        return kind;
    }

    public List<EventSeat> getTickets() {
        return tickets;
    }

    public void addTicket(EventSeat ticket) {
        tickets.add(ticket);
    }

    public void mark(RefundStatus status) {
        this.status = status;
    }
}
