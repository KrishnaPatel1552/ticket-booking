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
class WaitlistApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void customerJoinsTheWaitlistOnlyWhenNoSeatIsLeftToSell() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        ApiSession grace = signIn("grace@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();

        ResponseEntity<JsonNode> tooSoon = ada.post("/api/events/" + eventId + "/waitlist", java.util.Map.of());

        assertThat(tooSoon.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ada.get("/api/events/" + eventId + "/waitlist").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");

        sell(ada, eventId, opened, List.of("A1", "A2", "A3", "A4", "A5", "A6"));
        sell(ada, eventId, opened, List.of("A7", "A8", "A9", "A10", "A11", "A12"));
        sell(grace, eventId, opened, List.of("B1", "B2", "B3", "B4", "B5", "B6"));
        sell(grace, eventId, opened, List.of("B7", "B8"));

        ResponseEntity<JsonNode> adaJoined = ada.post("/api/events/" + eventId + "/waitlist", java.util.Map.of());
        ResponseEntity<JsonNode> graceJoined = grace.post("/api/events/" + eventId + "/waitlist", java.util.Map.of());

        assertThat(adaJoined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(adaJoined.getBody().get("place").asInt()).isEqualTo(1);
        assertThat(graceJoined.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(graceJoined.getBody().get("place").asInt()).isEqualTo(2);
        assertThat(ada.get("/api/events/" + eventId + "/waitlist").getBody().get("place").asInt()).isEqualTo(1);
        assertThat(grace.get("/api/events/" + eventId + "/waitlist").getBody().get("place").asInt()).isEqualTo(2);
        assertThat(ada.post("/api/events/" + eventId + "/waitlist", java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(grace.get("/api/events/" + eventId + "/waitlist").getBody().get("place").asInt()).isEqualTo(2);

        JsonNode soldOut = ada.get("/api/events/" + eventId).getBody();
        for (String label : List.of("A1", "A12", "B1", "B8")) {
            assertThat(seatState(soldOut, label)).isEqualTo("ticket");
        }
    }

    private void sell(ApiSession customer, long eventId, JsonNode map, List<String> labels) {
        List<Long> seatIds = labels.stream().map(label -> seatId(map, label)).toList();
        ResponseEntity<JsonNode> held = customer.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", seatIds));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        ResponseEntity<JsonNode> paid = customer.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", seatIds,
                "mock", "success"));
        assertThat(paid.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private long createEvent(ApiSession admin) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "Waitlist-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-06-01T01:00:00Z",
                "timeZone", "America/New_York"));
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

    private static String seatState(JsonNode event, String label) {
        for (JsonNode section : event.get("sections")) {
            for (JsonNode seat : section.get("seats")) {
                if (label.equals(seat.get("label").asText())) {
                    return seat.get("state").asText();
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
