package ticketbooking;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CustomerApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void customerSignsInWithEmailAndPassword() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("ada@example.com");
        assertThat(response.getBody().get("role").asText()).isEqualTo("customer");

        ResponseEntity<JsonNode> me = session.get("/api/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email").asText()).isEqualTo("ada@example.com");
        assertThat(me.getBody().get("role").asText()).isEqualTo("customer");
    }

    @Test
    void wrongPasswordLeavesCustomerSignedOut() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "wrong-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<JsonNode> me = session.get("/api/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void signingInAgainOnTheSameSessionReplacesTheCustomer() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> ada = session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "password"));
        assertThat(ada.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(session.get("/api/me").getBody().get("email").asText()).isEqualTo("ada@example.com");

        ResponseEntity<JsonNode> grace = session.post("/api/sign-in", java.util.Map.of(
                "email", "grace@example.com",
                "password", "password"));
        assertThat(grace.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(grace.getBody().get("email").asText()).isEqualTo("grace@example.com");
        assertThat(session.get("/api/me").getBody().get("email").asText()).isEqualTo("grace@example.com");
    }

    @Test
    void signOutEndsTheSession() {
        ApiSession session = new ApiSession();
        assertThat(session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "password")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> signedOut = session.postNoContent("/api/sign-out");
        assertThat(signedOut.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(session.get("/api/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void twoTabsStaySignedInAsDifferentCustomers() {
        ResponseEntity<JsonNode> ada = signInForToken("ada@example.com");
        ResponseEntity<JsonNode> grace = signInForToken("grace@example.com");
        String adaToken = ada.getBody().get("token").asText();
        String graceToken = grace.getBody().get("token").asText();

        assertThat(adaToken).isNotEqualTo(graceToken);
        assertThat(meAs(adaToken).getBody().get("email").asText()).isEqualTo("ada@example.com");
        assertThat(meAs(graceToken).getBody().get("email").asText()).isEqualTo("grace@example.com");
    }

    @Test
    void signingOutOneTabLeavesTheOtherCustomerSignedIn() {
        String adaToken = signInForToken("ada@example.com").getBody().get("token").asText();
        String graceToken = signInForToken("grace@example.com").getBody().get("token").asText();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adaToken);
        ResponseEntity<Void> signedOut = rest.exchange(
                "/api/sign-out", HttpMethod.POST, new HttpEntity<>(headers), Void.class);

        assertThat(signedOut.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(meAs(adaToken).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(meAs(graceToken).getBody().get("email").asText()).isEqualTo("grace@example.com");
    }

    @Test
    void secondCustomerSignsInAsACustomer() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "grace@example.com",
                "password", "password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("grace@example.com");
        assertThat(response.getBody().get("role").asText()).isEqualTo("customer");
    }

    @Test
    void adminSignsInAsAnAdmin() {
        ApiSession session = new ApiSession();

        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", "admin@example.com",
                "password", "password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("email").asText()).isEqualTo("admin@example.com");
        assertThat(response.getBody().get("role").asText()).isEqualTo("admin");

        ResponseEntity<JsonNode> me = session.get("/api/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("role").asText()).isEqualTo("admin");
    }

    @Test
    void customerIsRefusedOnAnAdminEndpoint() {
        ApiSession session = new ApiSession();
        ResponseEntity<JsonNode> signIn = session.post("/api/sign-in", java.util.Map.of(
                "email", "ada@example.com",
                "password", "password"));
        assertThat(signIn.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refused = session.post("/api/admin/events", java.util.Map.of(
                "name", "Should Not Exist",
                "startsAt", "2026-12-20T01:00:00Z",
                "timeZone", "America/New_York"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void searchByNameReturnsTheStarterEvent() {
        ApiSession ada = signIn("ada@example.com");

        ResponseEntity<JsonNode> response = ada.get("/api/events?name=lights");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode matches = response.getBody();
        assertThat(matches.isArray()).isTrue();
        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).get("name").asText()).isEqualTo("Harbor Lights");
        assertThat(matches.get(0).get("venueName").asText()).isEqualTo("Harbor Hall");
        assertThat(matches.get(0).get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(matches.get(0).get("timeZone").asText()).isEqualTo("America/New_York");
    }

    @Test
    void openingAnEventReturnsTheSeatMapBySection() {
        ApiSession ada = signIn("ada@example.com");
        long eventId = ada.get("/api/events?name=lights").getBody().get(0).get("id").asLong();

        ResponseEntity<JsonNode> response = ada.get("/api/events/" + eventId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode event = response.getBody();
        assertThat(event.get("name").asText()).isEqualTo("Harbor Lights");
        assertThat(event.get("earlyBirdStartsAt").asText()).isEqualTo("2026-10-01T00:00:00Z");
        assertThat(event.get("earlyBirdEndsAt").asText()).isEqualTo("2026-11-15T00:00:00Z");

        JsonNode sections = event.get("sections");
        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).get("name").asText()).isEqualTo("Orchestra");
        assertThat(seatLabels(sections.get(0))).containsExactly(
                "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12");
        assertThat(sections.get(1).get("name").asText()).isEqualTo("Balcony");
        assertThat(seatLabels(sections.get(1))).containsExactly(
                "B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8");

        for (JsonNode section : sections) {
            for (JsonNode seat : section.get("seats")) {
                assertThat(seat.get("priceCents").asInt()).isEqualTo(5000);
                assertThat(seat.get("earlyBirdPriceCents").asInt()).isEqualTo(4000);
                assertThat(seat.get("state").asText()).isEqualTo("free");
            }
        }
    }

    @Test
    void searchReturnsAnEventTheAdminCreated() {
        ApiSession admin = signIn("admin@example.com");
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "CedarJazz-" + UUID.randomUUID();

        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2026-12-20T01:00:00Z",
                "timeZone", "America/Chicago",
                "earlyBirdStartsAt", "2026-11-01T00:00:00Z",
                "earlyBirdEndsAt", "2026-12-01T00:00:00Z"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ApiSession grace = signIn("grace@example.com");
        JsonNode match = findNamed(grace.get("/api/events?name=" + name).getBody(), name);
        assertThat(match.get("startsAt").asText()).isEqualTo("2026-12-20T01:00:00Z");
        assertThat(match.get("timeZone").asText()).isEqualTo("America/Chicago");
        assertThat(match.get("venueId").asLong()).isEqualTo(venueId);

        JsonNode opened = grace.get("/api/events/" + match.get("id").asLong()).getBody();
        assertThat(opened.get("earlyBirdStartsAt").asText()).isEqualTo("2026-11-01T00:00:00Z");
        assertThat(opened.get("earlyBirdEndsAt").asText()).isEqualTo("2026-12-01T00:00:00Z");
    }

    @Test
    void secondEventAtTheSameVenueShowsTheSameSeatsFree() {
        ApiSession admin = signIn("admin@example.com");
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "SecondNight-" + UUID.randomUUID();

        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-01-10T01:00:00Z",
                "timeZone", "America/New_York"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ApiSession ada = signIn("ada@example.com");
        JsonNode match = findNamed(ada.get("/api/events?name=" + name).getBody(), name);
        JsonNode opened = ada.get("/api/events/" + match.get("id").asLong()).getBody();
        assertThat(opened.get("earlyBirdStartsAt").isNull()).isTrue();
        assertThat(opened.get("earlyBirdEndsAt").isNull()).isTrue();
        assertThat(opened.get("sections")).hasSize(2);
        assertThat(opened.get("sections").get(0).get("name").asText()).isEqualTo("Orchestra");
        assertThat(seatLabels(opened.get("sections").get(0))).containsExactly(
                "A1", "A2", "A3", "A4", "A5", "A6", "A7", "A8", "A9", "A10", "A11", "A12");
        assertThat(opened.get("sections").get(1).get("name").asText()).isEqualTo("Balcony");
        assertThat(seatLabels(opened.get("sections").get(1))).containsExactly(
                "B1", "B2", "B3", "B4", "B5", "B6", "B7", "B8");
        for (JsonNode section : opened.get("sections")) {
            for (JsonNode seat : section.get("seats")) {
                assertThat(seat.get("state").asText()).isEqualTo("free");
                assertThat(seat.get("priceCents").asInt()).isEqualTo(5000);
            }
        }
    }

    @Test
    void adminCreatesAPriceTierWithAnEarlyBirdPrice() {
        ApiSession admin = signIn("admin@example.com");
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "BalconyPlus-" + UUID.randomUUID();

        ResponseEntity<JsonNode> created = admin.post("/api/admin/price-tiers", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "priceCents", 7500,
                "earlyBirdPriceCents", 6000));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode match = findNamed(admin.get("/api/price-tiers").getBody(), name);
        assertThat(match.get("priceCents").asInt()).isEqualTo(7500);
        assertThat(match.get("earlyBirdPriceCents").asInt()).isEqualTo(6000);
        assertThat(match.get("venueId").asLong()).isEqualTo(venueId);
    }

    @Test
    void adminCreatesAPriceTierWithoutAnEarlyBirdPrice() {
        ApiSession admin = signIn("admin@example.com");
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "StandardLate-" + UUID.randomUUID();

        ResponseEntity<JsonNode> created = admin.post("/api/admin/price-tiers", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "priceCents", 3200));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode match = findNamed(admin.get("/api/price-tiers").getBody(), name);
        assertThat(match.get("priceCents").asInt()).isEqualTo(3200);
        assertThat(match.get("earlyBirdPriceCents").isNull()).isTrue();
    }

    @Test
    void testClockMovesTheServerClock() {
        ApiSession admin = signIn("admin@example.com");

        ResponseEntity<JsonNode> moved = admin.post("/api/test/clock", java.util.Map.of(
                "instant", "2030-01-15T12:00:00Z"));
        assertThat(moved.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> read = admin.get("/api/test/clock");
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().get("instant").asText()).isEqualTo("2030-01-15T12:00:00Z");
    }

    @Test
    void starterSaleTermsAreTheStoredGroupRateAndServiceFee() {
        ApiSession ada = signIn("ada@example.com");

        ResponseEntity<JsonNode> response = ada.get("/api/sale-terms");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("groupRateThreshold").asInt()).isEqualTo(4);
        assertThat(response.getBody().get("groupRateAmountCents").asInt()).isEqualTo(500);
        assertThat(response.getBody().get("serviceFeeCents").asInt()).isEqualTo(100);
    }

    private static JsonNode findNamed(JsonNode events, String name) {
        for (JsonNode event : events) {
            if (name.equals(event.get("name").asText())) {
                return event;
            }
        }
        throw new AssertionError("No event named " + name);
    }

    private static List<String> seatLabels(JsonNode section) {
        List<String> labels = new ArrayList<>();
        section.get("seats").forEach(seat -> labels.add(seat.get("label").asText()));
        return labels;
    }

    private ResponseEntity<JsonNode> signInForToken(String email) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<JsonNode> response = rest.exchange(
                "/api/sign-in",
                HttpMethod.POST,
                new HttpEntity<>(java.util.Map.of("email", email, "password", "password"), headers),
                JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("token").asText()).isNotBlank();
        return response;
    }

    private ResponseEntity<JsonNode> meAs(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
    }

    private ApiSession signIn(String email) {
        ApiSession session = new ApiSession();
        ResponseEntity<JsonNode> response = session.post("/api/sign-in", java.util.Map.of(
                "email", email,
                "password", "password"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return session;
    }

    private final class ApiSession {
        private String cookieHeader = "";

        private ResponseEntity<JsonNode> post(String path, Object body) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            if (!cookieHeader.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookieHeader);
            }
            ResponseEntity<JsonNode> response = rest.exchange(
                    path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
            remember(response);
            return response;
        }

        private ResponseEntity<JsonNode> get(String path) {
            HttpHeaders headers = new HttpHeaders();
            if (!cookieHeader.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookieHeader);
            }
            return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class);
        }

        private ResponseEntity<Void> postNoContent(String path) {
            HttpHeaders headers = new HttpHeaders();
            if (!cookieHeader.isEmpty()) {
                headers.set(HttpHeaders.COOKIE, cookieHeader);
            }
            ResponseEntity<Void> response = rest.exchange(
                    path, HttpMethod.POST, new HttpEntity<>(headers), Void.class);
            remember(response);
            return response;
        }

        private void remember(ResponseEntity<?> response) {
            List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
            if (setCookies == null || setCookies.isEmpty()) {
                return;
            }
            cookieHeader = setCookies.stream()
                    .map(cookie -> cookie.split(";", 2)[0])
                    .collect(Collectors.joining("; "));
        }
    }
}
