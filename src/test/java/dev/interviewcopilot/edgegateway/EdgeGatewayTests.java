package dev.interviewcopilot.edgegateway;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest
@AutoConfigureWebTestClient
class EdgeGatewayTests {

    static HttpServer bff;

    @Autowired
    WebTestClient client;

    @BeforeAll
    static void startBff() throws IOException {
        bff = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        bff.createContext("/api/v1/echo", exchange -> {
            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            String auth = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = ("user=" + userId + ";auth=" + auth).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        bff.createContext("/api/v1/accounts/echo", exchange -> {
            String userId = exchange.getRequestHeaders().getFirst("X-User-Id");
            byte[] body = ("user=" + userId).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        bff.start();
    }

    @AfterAll
    static void stopBff() {
        bff.stop(0);
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("BFF_URL", () -> "http://127.0.0.1:" + bff.getAddress().getPort());
    }

    @Test
    void noTokenGets401() {
        client.get().uri("/api/v1/echo").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void badTokenGets401() {
        client.get().uri("/api/v1/echo").header("Authorization", "Bearer not-a-jwt")
                .exchange().expectStatus().isUnauthorized();
    }

    @Test
    void tokenWithoutUserScopeGets403() {
        client.mutateWith(mockJwt().authorities(() -> "SCOPE_other"))
                .get().uri("/api/v1/echo").exchange().expectStatus().isForbidden();
    }

    @Test
    void forgedUserIdNeverReachesBffAndTokenIsNotForwarded() {
        client.mutateWith(mockJwt().jwt(j -> j.subject("real-user"))
                        .authorities(() -> "SCOPE_interview-copilot/user"))
                .get().uri("/api/v1/echo").header("X-User-Id", "forged").exchange()
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("user=real-user;auth=null");
    }

    @Test
    void publicAccountRoutesNeedNoTokenButNeverCarryClientIdentity() {
        client.post().uri("/api/v1/accounts/echo").header("X-User-Id", "forged").exchange()
                .expectStatus().isOk().expectBody(String.class).isEqualTo("user=null");
    }

    @Test
    void otherRoutesAreNotPublic() {
        client.post().uri("/api/v1/echo").exchange().expectStatus().isUnauthorized();
        client.get().uri("/api/v1/accounts/echo").exchange().expectStatus().isUnauthorized();
    }

    @Test
    void unknownApiVersionsAreNotRouted() {
        client.mutateWith(mockJwt().authorities(() -> "SCOPE_interview-copilot/user"))
                .get().uri("/api/v2/echo").exchange().expectStatus().isNotFound();
    }

    @Test
    void publicRoutesAreRateLimitedPerClientAddress() {
        for (int i = 0; i < 20; i++) {
            client.post().uri("/api/v1/accounts/echo").header("X-Forwarded-For", "203.0.113.9").exchange();
        }
        client.post().uri("/api/v1/accounts/echo").header("X-Forwarded-For", "203.0.113.9").exchange()
                .expectStatus().isEqualTo(429);
        client.post().uri("/api/v1/accounts/echo").header("X-Forwarded-For", "203.0.113.10").exchange()
                .expectStatus().isOk();
    }
}
