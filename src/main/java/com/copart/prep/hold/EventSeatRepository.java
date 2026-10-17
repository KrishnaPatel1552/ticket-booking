package com.copart.prep.hold;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    List<EventSeat> findByEventIdAndActiveTrue(Long eventId);

    Optional<EventSeat> findByEventIdAndSeatIdAndActiveTrue(Long eventId, Long seatId);

    List<EventSeat> findByOrderIdAndActiveTrueAndKind(Long orderId, EventSeatKind kind);

    long countByEventIdAndActiveTrueAndKind(Long eventId, EventSeatKind kind);

    long countByEventIdAndKind(Long eventId, EventSeatKind kind);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select claim
            from EventSeat claim
            where claim.order.id = :orderId
              and claim.active = true
              and claim.seat.id in :seatIds
            """)
    List<EventSeat> lockActiveForSeats(@Param("orderId") long orderId, @Param("seatIds") List<Long> seatIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select claim
            from EventSeat claim
            where claim.order.id = :orderId
              and claim.active = true
            """)
    List<EventSeat> lockActiveOnOrder(@Param("orderId") long orderId);

    @Query("""
            select count(claim)
            from EventSeat claim
            where claim.order.id = :orderId
              and claim.active = true
            """)
    long countActiveOnOrder(@Param("orderId") long orderId);

    @Query("""
            select count(claim)
            from EventSeat claim
            where claim.order.customer.id = :customerId
              and claim.event.id = :eventId
              and claim.active = true
            """)
    long countActiveForCustomerAtEvent(@Param("customerId") long customerId, @Param("eventId") long eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select claim
            from EventSeat claim
            where claim.event.id = :eventId
              and claim.active = true
              and claim.kind = :kind
              and claim.heldUntil <= :now
              and not exists (
                  select payment.id
                  from Payment payment
                  where payment.order = claim.order
              )
            """)
    List<EventSeat> lockExpiredHolds(
            @Param("eventId") long eventId,
            @Param("kind") EventSeatKind kind,
            @Param("now") Instant now);
}
