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
class CancelApiTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void atExactlyOneHundredSixtyEightHoursAPartialCancelRefundsThatTicketsFullNet() {
        ApiSession admin = signIn("admin@example.com");
        long eventId = createEvent(admin, "2026-12-15T01:00:00Z");
        ApiSession ada = signIn("ada@example.com");
        assertThat(ada.post("/api/test/clock", java.util.Map.of("instant", "2026-12-08T01:00:00Z")).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        JsonNode opened = ada.get("/api/events/" + eventId).getBody();
        long a1 = seatId(opened, "A1");
        long a2 = seatId(opened, "A2");
        ResponseEntity<JsonNode> held = ada.post("/api/events/" + eventId + "/holds", java.util.Map.of(
                "seatIds", List.of(a1, a2)));
        assertThat(held.getStatusCode()).isEqualTo(HttpStatus.OK);
        long orderId = held.getBody().get("orderId").asLong();
        assertThat(ada.post("/api/orders/" + orderId + "/checkout", java.util.Map.of(
                "seatIds", List.of(a1, a2),
                "mock", "success")).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<JsonNode> cancelled = ada.post("/api/orders/" + orderId + "/cancel", java.util.Map.of(
                "seatIds", List.of(a1),
                "mock", "success"));

        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode order = cancelled.getBody();
        assertThat(order.get("payment").get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("payment").get("amountCents").asInt()).isEqualTo(10100);
        assertThat(order.get("refunds")).hasSize(1);
        assertThat(order.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(order.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(order)).containsExactly("A2");
        JsonNode after = ada.get("/api/events/" + eventId).getBody();
        assertThat(seatState(after, "A1")).isEqualTo("free");
        assertThat(seatState(after, "A2")).isEqualTo("ticket");
        JsonNode again = ada.get("/api/orders/" + orderId).getBody();
        assertThat(again.get("refunds").get(0).get("status").asText()).isEqualTo("succeeded");
        assertThat(again.get("refunds").get(0).get("amountCents").asInt()).isEqualTo(5000);
        assertThat(ticketLabels(again)).containsExactly("A2");
    }

    private long createEvent(ApiSession admin, String startsAt) {
        long venueId = admin.get("/api/events?name=lights").getBody().get(0).get("venueId").asLong();
        String name = "CancelNight-" + UUID.randomUUID();
        ResponseEntity<JsonNode> created = admin.post("/api/admin/events", java.util.Map.of(
                "name", name,
                "venueId", venueId,
                "startsAt", startsAt,
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
        return seat(event, label).get("id").asLong();
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

    private static List<String> ticketLabels(JsonNode order) {
        List<String> labels = new java.util.ArrayList<>();
        for (JsonNode ticket : order.get("tickets")) {
            labels.add(ticket.get("label").asText());
        }
        return labels;
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
