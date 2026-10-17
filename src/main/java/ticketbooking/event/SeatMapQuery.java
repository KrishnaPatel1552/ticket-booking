package ticketbooking.event;

import ticketbooking.block.SeatBlockRepository;
import ticketbooking.hold.EventSeat;
import ticketbooking.hold.EventSeatKind;
import ticketbooking.hold.EventSeatRepository;
import ticketbooking.hold.ExpiredHoldRelease;
import ticketbooking.signin.Account;
import ticketbooking.signin.AccountRepository;
import ticketbooking.venue.Seat;
import ticketbooking.venue.SeatRepository;
import ticketbooking.venue.SectionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SeatMapQuery {

    private final AccountRepository accounts;
    private final EventRepository events;
    private final SectionRepository sections;
    private final SeatRepository seats;
    private final EventSeatRepository eventSeats;
    private final SeatBlockRepository seatBlocks;
    private final ExpiredHoldRelease expiredHoldRelease;

    public SeatMapQuery(
            AccountRepository accounts,
            EventRepository events,
            SectionRepository sections,
            SeatRepository seats,
            EventSeatRepository eventSeats,
            SeatBlockRepository seatBlocks,
            ExpiredHoldRelease expiredHoldRelease) {
        this.accounts = accounts;
        this.events = events;
        this.sections = sections;
        this.seats = seats;
        this.eventSeats = eventSeats;
        this.seatBlocks = seatBlocks;
        this.expiredHoldRelease = expiredHoldRelease;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventSeatMap open(long eventId, String email) {
        expiredHoldRelease.release(eventId);
        Event event = events.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Long viewerId = email == null
                ? null
                : accounts.findByEmail(email).map(Account::getId).orElse(null);
        List<EventSeat> activeClaims = eventSeats.findByEventIdAndActiveTrue(event.getId());
        Map<Long, EventSeat> activeBySeatId = activeClaims.stream()
                .collect(Collectors.toMap(claim -> claim.getSeat().getId(), Function.identity()));
        Set<Long> blocked = seatBlocks.findByEventIdAndActiveTrue(event.getId()).stream()
                .map(block -> block.getSeat().getId())
                .collect(Collectors.toSet());
        Long activeHoldOrderId = null;
        Set<Long> paidOrderIds = new LinkedHashSet<>();
        if (viewerId != null) {
            List<EventSeat> mine = eventSeats.findByOrderCustomerIdAndEventIdAndActiveTrueOrderByIdAsc(
                    viewerId,
                    event.getId());
            for (EventSeat claim : mine) {
                if (claim.getKind() == EventSeatKind.HOLD) {
                    activeHoldOrderId = claim.getOrder().getId();
                } else if (claim.getKind() == EventSeatKind.TICKET) {
                    paidOrderIds.add(claim.getOrder().getId());
                }
            }
        }
        List<SectionSeats> sectionMaps = sections.findByVenueIdOrderByIdAsc(event.getVenue().getId()).stream()
                .map(section -> new SectionSeats(
                        section.getName(),
                        seats.findBySectionIdOrderByIdAsc(section.getId()).stream()
                                .map(seat -> toSeatView(seat, activeBySeatId, blocked, event.isCancelled(), viewerId))
                                .toList()))
                .toList();
        return new EventSeatMap(
                event.getId(),
                event.getName(),
                event.getVenue().getId(),
                event.getVenue().getName(),
                event.getStartsAt(),
                event.getTimeZone(),
                event.getEarlyBirdStartsAt(),
                event.getEarlyBirdEndsAt(),
                sectionMaps,
                event.isCancelled(),
                event.getRescheduledAt() != null,
                activeHoldOrderId,
                new ArrayList<>(paidOrderIds));
    }

    private static SeatView toSeatView(
            Seat seat,
            Map<Long, EventSeat> activeBySeatId,
            Set<Long> blocked,
            boolean eventCancelled,
            Long viewerId) {
        EventSeat claim = activeBySeatId.get(seat.getId());
        SeatState state = stateOf(seat, claim, blocked, eventCancelled);
        boolean yours = claim != null
                && viewerId != null
                && claim.getOrder() != null
                && viewerId.equals(claim.getOrder().getCustomer().getId());
        Instant heldUntil = yours && claim.getKind() == EventSeatKind.HOLD ? claim.getHeldUntil() : null;
        return new SeatView(
                seat.getId(),
                seat.getLabel(),
                seat.getPriceTier().getPriceCents(),
                seat.getPriceTier().getEarlyBirdPriceCents(),
                state,
                yours,
                heldUntil);
    }

    private static SeatState stateOf(
            Seat seat,
            EventSeat claim,
            Set<Long> blocked,
            boolean eventCancelled) {
        if (claim != null) {
            return claim.getKind().seatState();
        }
        if (blocked.contains(seat.getId()) || eventCancelled) {
            return SeatState.BLOCKED;
        }
        return SeatState.FREE;
    }
}
