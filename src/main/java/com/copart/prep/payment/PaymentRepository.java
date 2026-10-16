package com.copart.prep.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Query("""
            select count(payment) > 0
            from Payment payment
            where payment.order.id = :orderId
            """)
    boolean existsForOrder(@Param("orderId") long orderId);
}