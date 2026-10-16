package com.copart.prep.starter;

import com.copart.prep.event.Event;
import com.copart.prep.event.EventRepository;
import com.copart.prep.pricetier.PriceTier;
import com.copart.prep.pricetier.PriceTierRepository;
import com.copart.prep.signin.Account;
import com.copart.prep.signin.AccountRepository;
import com.copart.prep.signin.AccountRole;
import com.copart.prep.venue.Seat;
import com.copart.prep.venue.SeatRepository;
import com.copart.prep.venue.Section;
import com.copart.prep.venue.SectionRepository;
import com.copart.prep.venue.Venue;
import com.copart.prep.venue.VenueRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class StarterData implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(StarterData.class);

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final VenueRepository venues;
    private final EventRepository events;
    private final PriceTierRepository priceTiers;
    private final SectionRepository sections;
    private final SeatRepository seats;

    public StarterData(
            AccountRepository accounts,
            PasswordEncoder passwordEncoder,
            VenueRepository venues,
            EventRepository events,
            PriceTierRepository priceTiers,
            SectionRepository sections,
            SeatRepository seats) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.venues = venues;
        this.events = events;
        this.priceTiers = priceTiers;
        this.sections = sections;
        this.seats = seats;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAccount("ada@example.com", AccountRole.CUSTOMER);
        seedAccount("grace@example.com", AccountRole.CUSTOMER);
        seedAccount("admin@example.com", AccountRole.ADMIN);
        seedVenueAndEvent();
    }

    private void seedVenueAndEvent() {
        Venue venue = venues.findByName("Harbor Hall")
                .orElseGet(() -> venues.save(new Venue("Harbor Hall", "America/New_York")));
        if (events.findByName("Harbor Lights").isEmpty()) {
            events.save(new Event(
                    venue,
                    "Harbor Lights",
                    Instant.parse("2026-12-15T01:00:00Z"),
                    "America/New_York",
                    Instant.parse("2026-10-01T00:00:00Z"),
                    Instant.parse("2026-11-15T00:00:00Z")));
            logger.info("Seeded venue {} and event Harbor Lights", venue.getName());
        }
        if (seats.count() > 0) {
            return;
        }
        PriceTier standard = priceTiers.save(new PriceTier(venue, "Standard", 5000, 4000));
        seedSection(venue, standard, "Orchestra", "A", 12);
        seedSection(venue, standard, "Balcony", "B", 8);
        logger.info("Seeded sections and seats for {}", venue.getName());
    }

    private void seedSection(Venue venue, PriceTier priceTier, String name, String prefix, int count) {
        Section section = sections.save(new Section(venue, name));
        for (int number = 1; number <= count; number++) {
            seats.save(new Seat(section, priceTier, prefix + number));
        }
    }

    private void seedAccount(String email, AccountRole role) {
        if (accounts.findByEmail(email).isPresent()) {
            return;
        }
        accounts.save(new Account(email, passwordEncoder.encode("password"), role));
        logger.info("Seeded {} {}", role.name().toLowerCase(), email);
    }
}
