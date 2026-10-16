package com.copart.prep.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_Id(long orderId);

    @Query("""
            select payment
            from Payment payment
            where payment.order.id = :orderId
              and (
                payment.status = com.copart.prep.payment.PaymentStatus.PENDING
                or payment.status = com.copart.prep.payment.PaymentStatus.UNKNOWN
                or (
                  payment.status = com.copart.prep.payment.PaymentStatus.SUCCEEDED
                  and not exists (
                    select ticket.id
                    from EventSeat ticket
                    where ticket.order = payment.order
                      and ticket.active = true
                      and ticket.kind = com.copart.prep.hold.EventSeatKind.TICKET
                  )
                )
              )
            """)
    Optional<Payment> findNeedingReconcile(@Param("orderId") long orderId);

    @Query("""
            select count(payment) > 0
            from Payment payment
            where payment.order.id = :orderId
            """)
    boolean existsForOrder(@Param("orderId") long orderId);
}