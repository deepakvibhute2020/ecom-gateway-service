package com.enncompany.ecomgatewayservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.jwt.secret=0123456789abcdef0123456789abcdef")
@AutoConfigureWebTestClient
class EcomGatewayServiceApplicationTests {

  private static final HttpServer USER_SERVICE = startFakeUserService();

  @Autowired private WebTestClient client;

  private static HttpServer startFakeUserService() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          "/internal/auth/verify",
          ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean ok = body.contains("\"secret\"");
            byte[] out =
                (ok ? "{\"userId\":7,\"loginId\":\"bob\",\"userType\":2}" : "").getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(ok ? 200 : 401, ok ? out.length : -1);
            if (ok) {
              ex.getResponseBody().write(out);
            }
            ex.close();
          });
      server.start();
      return server;
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    String id = "spring.cloud.discovery.client.simple.instances.ecom-masterdata-service[0]";
    registry.add("spring.cloud.kubernetes.enabled", () -> "false");
    registry.add(id + ".host", () -> "localhost");
    registry.add(id + ".port", () -> USER_SERVICE.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    USER_SERVICE.stop(0);
  }

  @Test
  void protectedRouteRequiresToken() {
    client.get().uri("/api/product").exchange().expectStatus().isUnauthorized();
  }

  @Test
  void invalidCredentialsRejected() {
    client
        .post()
        .uri("/auth/login")
        .bodyValue(Map.of("loginId", "bob", "password", "wrong"))
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void validLoginIssuesFifteenMinuteTokenAcceptedByGateway() {
    Map<?, ?> res =
        client
            .post()
            .uri("/auth/login")
            .bodyValue(Map.of("loginId", "bob", "password", "secret"))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(Map.class)
            .returnResult()
            .getResponseBody();

    assertThat(res.get("expiresIn")).isEqualTo(900);
    String token = (String) res.get("accessToken");

    // Authenticated: passes security and reaches the (fake) downstream, which has no such route.
    client
        .get()
        .uri("/api/product")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .value(s -> assertThat(s).isNotEqualTo(401));
  }
}
