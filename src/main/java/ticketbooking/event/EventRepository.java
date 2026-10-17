package ticketbooking.event;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByNameContainingIgnoreCaseOrderByIdAsc(String name);

    Optional<Event> findByName(String name);
}
