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
class RescheduleApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void anAdminReschedulesTheEventAndTheNextSeatMapShowsTheNewStart() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        assertThat(opened.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(opened, "A1")).isEqualTo("free");

        ResponseEntity<JsonNode> rescheduled = admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date"));

        assertThat(rescheduled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2027-03-01T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(seatState(map, "B8")).isEqualTo("free");
        ResponseEntity<JsonNode> entries = admin.get("/api/admin/events/" + eventId + "/audit-entries");
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entries.getBody()).hasSize(1);
        assertThat(entries.getBody().get(0).get("kind").asText()).isEqualTo("reschedule");
        assertThat(entries.getBody().get(0).get("reason").asText()).isEqualTo("The venue moved the date");
        assertThat(entries.getBody().get(0).get("seatId").isNull()).isTrue();
        assertThat(entries.getBody().get(0).get("recordedAt").asText()).isEqualTo("2026-10-16T15:00:00Z");
    }

    @Test
    void aCustomerIsRefusedAReschedule() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refused = ada.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "I cannot attend"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
    }

    @Test
    void aRescheduleWithoutAReasonIsRefused() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String path = "/api/admin/events/" + eventId + "/reschedule";

        assertThat(admin.post(path, java.util.Map.of("startsAt", "2027-03-01T01:00:00Z", "reason", "")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("startsAt", "2027-03-01T01:00:00Z", "reason", "   ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(path, java.util.Map.of("startsAt", "2027-03-01T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("free");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();
    }

    @Test
    void aCancelledEventIsRefusedAReschedule() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-10-16T15:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(admin.post("/api/admin/events/" + eventId + "/cancellation", java.util.Map.of(
                "reason", "The artist is ill",
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> refused = admin.post("/api/admin/events/" + eventId + "/reschedule", java.util.Map.of(
                "startsAt", "2027-03-01T01:00:00Z",
                "reason", "The venue moved the date"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode map = ada.get("/api/events/" + eventId).getBody();
        assertThat(map.get("startsAt").asText()).isEqualTo("2026-12-15T01:00:00Z");
        assertThat(seatState(map, "A1")).isEqualTo("blocked");
        JsonNode entries = admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("kind").asText()).isEqualTo("event-cancellation");
    }

    private long createEvent(ApiSession admin, String startsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "Reschedule-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", startsAt,
                "timeZone", "America/New_York",
                "earlyBirdStartsAt", "2026-10-01T00:00:00Z",
                "earlyBirdEndsAt", "2026-11-15T00:00:00Z"));
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

    private static String seatState(JsonNode event, String label) {
        return seat(event, label).get("state").asText();
    }

    private static JsonNode seat(JsonNode event, String label) {
        for (JsonNode section : event.get("sections")) {
            for (JsonNode candidate : section.get("seats")) {
                if (label.equals(candidate.get("label").asText())) {
                    return candidate;
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
