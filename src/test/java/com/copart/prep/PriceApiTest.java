package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
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
class PriceApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void twoSeatsInsideTheEarlyBirdWindowPriceAtTheEarlyBirdPricePlusTheFee() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(8100);
    }

    @Test
    void theEarlyBirdWindowIncludesItsStartAndExcludesItsEnd() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();

        assertThat(checkoutOne(ada, eventId, opened, "A1", "2026-10-01T00:00:00Z")).isEqualTo(4100);
        assertThat(checkoutOne(ada, eventId, opened, "A2", "2026-11-14T23:59:59Z")).isEqualTo(4100);
        assertThat(checkoutOne(ada, eventId, opened, "A3", "2026-09-30T23:59:59Z")).isEqualTo(5100);
        assertThat(checkoutOne(ada, eventId, opened, "A4", "2026-11-15T00:00:00Z")).isEqualTo(5100);
    }

    @Test
    void fourSeatsInsideTheEarlyBirdWindowTakeTheGroupRateOffOnce() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"), seatId(opened, "A4"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats,
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(15600);
    }

    @Test
    void threeSeatsInsideTheEarlyBirdWindowDoNotTakeTheGroupRate() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-10-01T00:00:00Z", "2026-11-15T00:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        List<Long> seats = List.of(seatId(opened, "A1"), seatId(opened, "A2"), seatId(opened, "A3"));
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of("seatIds", seats));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seats,
                "mock", "success"));

        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(12100);
    }

    @Test
    void anAdminCreatesAPromoCodeAndACustomerIsRefused() {
        ApiSession admin = signIn("admin@example.com");
        String code = "SAVE100-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "discountCents", 100,
                "globalLimit", 5,
                "perCustomerLimit", 1,
                "exclusive", false));

        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(created.getBody().get("code").asText()).isEqualTo(code);
        assertThat(created.getBody().get("discountCents").asInt()).isEqualTo(100);
        assertThat(created.getBody().get("globalLimit").asInt()).isEqualTo(5);
        assertThat(created.getBody().get("perCustomerLimit").asInt()).isEqualTo(1);
        assertThat(created.getBody().get("exclusive").asBoolean()).isFalse();

        ApiSession ada = signIn("ada@example.com");
        ResponseEntity<JsonNode> refused = ada.post("/api/admin/promo-codes", java.util.Map.of(
                "code", "NOPE-" + UUID.randomUUID(),
                "discountCents", 50,
                "globalLimit", 1,
                "perCustomerLimit", 1,
                "exclusive", true));
        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aPromoCodeComesOffTheTicketTotalAndLeavesTheServiceFee() {
        ApiSession admin = signIn("admin@example.com");
        String code = createPromo(admin, 100, 5, 2, false);
        long eventId = createPlainEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();

        ResponseEntity<JsonNode> attached = ada.post("/api/orders/" + orderId + "/promo-code", java.util.Map.of(
                "code", code));

        assertThat(attached.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(attached.getBody().get("promoCode").asText()).isEqualTo(code);
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt())
                .isEqualTo(5000);
    }

    private String createPromo(ApiSession admin, int discountCents, int globalLimit, int perCustomerLimit, boolean exclusive) {
        String code = "PROMO-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/promo-codes", java.util.Map.of(
                "code", code,
                "discountCents", discountCents,
                "globalLimit", globalLimit,
                "perCustomerLimit", perCustomerLimit,
                "exclusive", exclusive));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return code;
    }

    private long createPlainEvent(ApiSession admin) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "PriceNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-06-01T01:00:00Z",
                "timeZone", "America/New_York"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return findNamed(admin.get("/api/events?name=" + name).getBody(), name).get("id").asLong();
    }

    private int checkoutOne(ApiSession ada, long eventId, JsonNode opened, String label, String instant) {
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", instant)).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        long seat = seatId(opened, label);
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(seat)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> checkedOut = ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(seat),
                "mock", "success"));
        assertThat(checkedOut.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ada.get("/api/orders/" + orderId).getBody().get("payment").get("amountCents").asInt();
    }

    private long createEvent(ApiSession admin, String earlyBirdStartsAt, String earlyBirdEndsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "PriceNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-06-01T01:00:00Z",
                "timeZone", "America/New_York",
                "earlyBirdStartsAt", earlyBirdStartsAt,
                "earlyBirdEndsAt", earlyBirdEndsAt));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return findNamed(admin.get("/api/events?name=" + name).getBody(), name).get("id").asLong();
    }

    private static JsonNode findNamed(JsonNode events, String name) {
        for (JsonNode event : events) {
            if (name.equals(event.get("name").asText())) {
                return event;
            }
        }
        throw new AssertionError("No event named " + name);
    }

    private static long seatId(JsonNode event, String label) {
        for (JsonNode section : event.get("sections")) {
            for (JsonNode seat : section.get("seats")) {
                if (label.equals(seat.get("label").asText())) {
                    return seat.get("id").asLong();
                }
            }
        }
        throw new AssertionError("No seat labeled " + label);
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
