package com.copart.prep.hold;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EventSeatRepository extends JpaRepository<EventSeat, Long> {

    List<EventSeat> findByEventIdAndActiveTrue(Long eventId);

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
}
