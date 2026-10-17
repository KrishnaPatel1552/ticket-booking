package ticketbooking.pricetier;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceTierRepository extends JpaRepository<PriceTier, Long> {

    List<PriceTier> findAllByOrderByIdAsc();
}
