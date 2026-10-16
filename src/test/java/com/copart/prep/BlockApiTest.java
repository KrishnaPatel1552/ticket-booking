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
class BlockApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void adminBlocksAFreeSeatAndOpeningTheEventShowsItBlocked() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode before = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(before, "A1");

        ResponseEntity<JsonNode> blocked = admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/block",
                java.util.Map.of("reason", "Artist hold"));

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("blocked");
        assertThat(seatState(after, "A2")).isEqualTo("free");
        assertThat(seatState(after, "B1")).isEqualTo("free");
    }

    @Test
    void adminReleasesABlockAndOpeningTheEventShowsTheSeatFree() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        ResponseEntity<JsonNode> blocked = admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/block",
                java.util.Map.of("reason", "Artist hold"));
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> released = admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/release",
                java.util.Map.of("reason", "Artist is seated"));

        assertThat(released.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
    }

    @Test
    void blockingAndReleasingEachLeaveAnAuditEntryTheAdminCanRead() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        JsonNode opened = admin.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");

        assertThat(admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/block",
                java.util.Map.of("reason", "Artist hold")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/release",
                java.util.Map.of("reason", "Artist is seated")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> entries = admin.get("/api/admin/events/" + eventId + "/audit-entries");
        assertThat(entries.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entries.getBody()).hasSize(2);
        assertThat(entries.getBody().get(0).get("kind").asText()).isEqualTo("block");
        assertThat(entries.getBody().get(0).get("reason").asText()).isEqualTo("Artist hold");
        assertThat(entries.getBody().get(0).get("seatId").asLong()).isEqualTo(a1);
        assertThat(entries.getBody().get(1).get("kind").asText()).isEqualTo("release");
        assertThat(entries.getBody().get(1).get("reason").asText()).isEqualTo("Artist is seated");
        assertThat(entries.getBody().get(1).get("seatId").asLong()).isEqualTo(a1);
    }

    @Test
    void customerIsRefusedWhenBlockingOrReleasing() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");

        ResponseEntity<JsonNode> customerBlock = ada.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/block",
                java.util.Map.of("reason", "I want this seat"));
        assertThat(customerBlock.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");

        assertThat(admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/block",
                java.util.Map.of("reason", "Artist hold")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> customerRelease = ada.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/release",
                java.util.Map.of("reason", "Put it back"));
        assertThat(customerRelease.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(seatState(ada.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
    }

    @Test
    void adminIsRefusedWithoutAReason() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        JsonNode opened = admin.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        String blockPath = "/api/admin/events/" + eventId + "/seats/" + a1 + "/block";

        assertThat(admin.post(blockPath, java.util.Map.of("reason", "")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(blockPath, java.util.Map.of("reason", "   ")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(admin.post(blockPath, java.util.Map.of()).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(seatState(admin.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("free");
        assertThat(admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody()).isEmpty();

        assertThat(admin.post(blockPath, java.util.Map.of("reason", "Artist hold")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        String releasePath = "/api/admin/events/" + eventId + "/seats/" + a1 + "/release";
        assertThat(admin.post(releasePath, java.util.Map.of("reason", "")).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(seatState(admin.get("/api/events/" + eventId).getBody(), "A1")).isEqualTo("blocked");
        JsonNode entries = admin.get("/api/admin/events/" + eventId + "/audit-entries").getBody();
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).get("kind").asText()).isEqualTo("block");
    }

    @Test
    void holdOnABlockedSeatFails() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin);
        ApiSession ada = signIn("ada@example.com");
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        assertThat(admin.post(
                "/api/admin/events/" + eventId + "/seats/" + a1 + "/block",
                java.util.Map.of("reason", "Artist hold")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> held = ada.post(
                "/api/events/" + eventId + "/holds",
                java.util.Map.of("seatIds", List.of(a1)));

        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("blocked");
        assertThat(seatState(after, "A2")).isEqualTo("free");
    }

    private long createEvent(ApiSession admin) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "BlockNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", "2027-03-01T01:00:00Z",
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
