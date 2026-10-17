package ticketbooking.venue;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SeatRowLock {

    private final EntityManager entityManager;

    public SeatRowLock(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void lockInIdOrder(List<Long> seatIds) {
        List<Long> ordered = seatIds.stream().distinct().sorted().toList();
        for (Long seatId : ordered) {
            entityManager.createNativeQuery("""
                    select id
                    from seat
                    where id = :seatId
                    for update
                    """)
                    .setParameter("seatId", seatId)
                    .getSingleResult();
        }
    }
}
