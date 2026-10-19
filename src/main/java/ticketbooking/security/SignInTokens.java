package ticketbooking.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Sign-in tokens live in the tab that received them. The browser stores a token in
 * session storage and sends it back as a bearer token, so two tabs do not share a cookie.
 */
@Component
public class SignInTokens {

    private final ConcurrentHashMap<String, Authentication> tokens = new ConcurrentHashMap<>();

    public String issue(HttpServletRequest request, Authentication authentication) {
        String previous = bearer(request);
        if (previous != null) {
            tokens.remove(previous);
        }
        String token = UUID.randomUUID().toString();
        tokens.put(token, authentication);
        return token;
    }

    public Authentication authentication(String token) {
        return tokens.get(token);
    }

    public void revoke(HttpServletRequest request) {
        String token = bearer(request);
        if (token != null) {
            tokens.remove(token);
        }
    }

    public static String bearer(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || header.length() < "Bearer ".length()) {
            return null;
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return null;
        }
        String token = header.substring("Bearer ".length()).trim();
        return token.isEmpty() ? null : token;
    }
}
