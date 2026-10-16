package com.copart.prep;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
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
