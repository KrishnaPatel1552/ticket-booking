package com.copart.prep.refund;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrder_IdOrderByIdAsc(long orderId);

    @Query("""
            select refund
            from Refund refund
            join fetch refund.tickets
            where refund.id = :id
            """)
    Optional<Refund> findWithTickets(@Param("id") long id);

    @Query("""
            select count(refund) > 0
            from Refund refund
            join refund.tickets ticket
            where ticket.id in :eventSeatIds
              and refund.status in (
                com.copart.prep.refund.RefundStatus.PENDING,
                com.copart.prep.refund.RefundStatus.UNKNOWN
              )
            """)
    boolean existsOpenForTickets(@Param("eventSeatIds") Collection<Long> eventSeatIds);

    @Query("""
            select refund
            from Refund refund
            where refund.order.id = :orderId
              and refund.status in (
                com.copart.prep.refund.RefundStatus.PENDING,
                com.copart.prep.refund.RefundStatus.UNKNOWN
              )
            order by refund.id asc
            """)
    List<Refund> findNeedingLookup(@Param("orderId") long orderId);
}
