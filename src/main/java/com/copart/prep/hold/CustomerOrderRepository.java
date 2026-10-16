package com.copart.prep.hold;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, Long> {

    Optional<CustomerOrder> findByCustomerIdAndEventIdAndOpenForHoldsTrue(Long customerId, Long eventId);
}
