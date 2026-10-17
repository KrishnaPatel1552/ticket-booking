package ticketbooking.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrder_Id(long orderId);

    @Query("""
            select coalesce(sum(payment.amountCents), 0)
            from Payment payment
            where payment.order.event.id = :eventId
              and payment.status = ticketbooking.payment.PaymentStatus.SUCCEEDED
            """)
    long sumSucceededAmountCents(@Param("eventId") long eventId);

    @Query("""
            select payment
            from Payment payment
            where payment.order.id = :orderId
              and (
                payment.status = ticketbooking.payment.PaymentStatus.PENDING
                or payment.status = ticketbooking.payment.PaymentStatus.UNKNOWN
                or (
                  payment.status = ticketbooking.payment.PaymentStatus.SUCCEEDED
                  and not exists (
                    select ticket.id
                    from EventSeat ticket
                    where ticket.order = payment.order
                      and ticket.active = true
                      and ticket.kind = ticketbooking.hold.EventSeatKind.TICKET
                  )
                  and exists (
                    select hold.id
                    from EventSeat hold
                    where hold.order = payment.order
                      and hold.active = true
                      and hold.kind = ticketbooking.hold.EventSeatKind.HOLD
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

    @Query("""
            select count(payment)
            from Payment payment
            where payment.promoCode.id = :promoCodeId
              and payment.order.event.cancelledAt is null
              and payment.status in (
                ticketbooking.payment.PaymentStatus.PENDING,
                ticketbooking.payment.PaymentStatus.UNKNOWN,
                ticketbooking.payment.PaymentStatus.SUCCEEDED
              )
            """)
    long countTaken(@Param("promoCodeId") long promoCodeId);

    @Query("""
            select count(payment)
            from Payment payment
            where payment.promoCode.id = :promoCodeId
              and payment.order.customer.id = :customerId
              and payment.order.event.cancelledAt is null
              and payment.status in (
                ticketbooking.payment.PaymentStatus.PENDING,
                ticketbooking.payment.PaymentStatus.UNKNOWN,
                ticketbooking.payment.PaymentStatus.SUCCEEDED
              )
            """)
    long countTakenByCustomer(@Param("promoCodeId") long promoCodeId, @Param("customerId") long customerId);
}