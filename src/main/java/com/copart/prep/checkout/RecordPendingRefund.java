package com.copart.prep.checkout;

import com.copart.prep.clock.AppClock;
import com.copart.prep.event.Event;
import com.copart.prep.hold.CustomerOrder;
import com.copart.prep.hold.CustomerOrderRepository;
import com.copart.prep.hold.EventSeat;
import com.copart.prep.hold.EventSeatKind;
import com.copart.prep.hold.EventSeatRepository;
import com.copart.prep.refund.Refund;
import com.copart.prep.refund.RefundKind;
import com.copart.prep.refund.RefundRepository;
import com.copart.prep.refund.RefundStatus;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecordPendingRefund {

    private final AccountRepository accounts;
    private final CustomerOrderRepository orders;
    private final EventSeatRepository eventSeats;
    private final RefundRepository refunds;
    private final AppClock clock;
    private final EntityManager entityManager;

    public RecordPendingRefund(
            AccountRepository accounts,
            CustomerOrderRepository orders,
            EventSeatRepository eventSeats,
            RefundRepository refunds,
            AppClock clock,
            EntityManager entityManager) {
        this.accounts = accounts;
        this.orders = orders;
        this.eventSeats = eventSeats;
        this.refunds = refunds;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public long record(long orderId, List<Long> seatIds, String email) {
        Account customer = accounts.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        CustomerOrder order = orders.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!order.getCustomer().getId().equals(customer.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (seatIds.size() != new HashSet<>(seatIds).size()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        List<EventSeat> tickets = eventSeats.lockActiveForSeats(orderId, seatIds);
        if (!customerTickets(tickets, seatIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        List<Long> ticketIds = new ArrayList<>();
        for (EventSeat ticket : tickets) {
            ticketIds.add(ticket.getId());
        }
        if (refunds.existsOpenForTickets(ticketIds)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        Instant now = clock.now();
        Integer repaired = failedAmountForSameTickets(orderId, tickets);
        int amount = repaired != null
                ? repaired
                : amountCents(tickets, now, order.getEvent());
        Refund refund = new Refund(order, amount, RefundStatus.PENDING, now, RefundKind.CUSTOMER_CANCEL);
        for (EventSeat ticket : tickets) {
            refund.addTicket(ticket);
        }
        refunds.save(refund);
        entityManager.flush();
        return refund.getId();
    }

    private static boolean customerTickets(List<EventSeat> tickets, List<Long> seatIds) {
        if (tickets.size() != seatIds.size()) {
            return false;
        }
        for (EventSeat ticket : tickets) {
            if (ticket.getKind() != EventSeatKind.TICKET || ticket.getStartingPriceCents() == null
                    || ticket.getGroupShareCents() == null || ticket.getPromoShareCents() == null) {
                return false;
            }
        }
        return true;
    }

    private Integer failedAmountForSameTickets(long orderId, List<EventSeat> tickets) {
        HashSet<Long> requested = new HashSet<>();
        for (EventSeat ticket : tickets) {
            requested.add(ticket.getId());
        }
        Integer amount = null;
        for (Refund refund : refunds.findByOrder_IdAndStatusOrderByIdAsc(orderId, RefundStatus.FAILED)) {
            HashSet<Long> covered = new HashSet<>();
            for (EventSeat ticket : refund.getTickets()) {
                covered.add(ticket.getId());
            }
            if (covered.equals(requested)) {
                amount = refund.getAmountCents();
            }
        }
        return amount;
    }

    private static int amountCents(List<EventSeat> tickets, Instant now, Event event) {
        if (withinRescheduleWindow(now, event.getRescheduledAt())) {
            int amount = 0;
            for (EventSeat ticket : tickets) {
                amount += net(ticket) + ticket.getFeeShareCents();
            }
            return amount;
        }
        int amount = 0;
        for (EventSeat ticket : tickets) {
            amount += RefundBand.cents(net(ticket), now, event.getStartsAt());
        }
        return amount;
    }

    private static boolean withinRescheduleWindow(Instant now, Instant override) {
        if (override == null) {
            return false;
        }
        Duration elapsed = Duration.between(override, now);
        return !elapsed.isNegative() && elapsed.compareTo(Duration.ofHours(336)) <= 0;
    }

    private static int net(EventSeat ticket) {
        return ticket.getStartingPriceCents() - ticket.getGroupShareCents() - ticket.getPromoShareCents();
    }
}
