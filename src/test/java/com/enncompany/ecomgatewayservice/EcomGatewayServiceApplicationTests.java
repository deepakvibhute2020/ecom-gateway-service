package com.enncompany.ecomgatewayservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "app.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
@AutoConfigureWebTestClient
class EcomGatewayServiceApplicationTests {

  private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final String ISSUER = "ecom-userauth-service";
  private static final HttpServer AUTH_SERVICE = fake("/api", false);
  private static final HttpServer MASTERDATA_SERVICE = fake("/", true);

  @Autowired private WebTestClient client;

  /** Fake downstream; the masterdata one echoes the identity headers it receives. */
  private static HttpServer fake(String path, boolean echoHeaders) {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
      server.createContext(
          path,
          ex -> {
            String id = ex.getRequestHeaders().getFirst("X-User-Id");
            String role = ex.getRequestHeaders().getFirst("X-User-Role");
            String body =
                "{\"token\":\"t\",\"id\":\"" + id + "\",\"role\":\"" + role + "\"}";
            byte[] out = body.getBytes();
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
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
    registry.add("spring.cloud.kubernetes.enabled", () -> "false");
    String auth = "spring.cloud.discovery.client.simple.instances.ecom-userauth-service[0]";
    String md = "spring.cloud.discovery.client.simple.instances.ecom-masterdata-service[0]";
    registry.add(auth + ".host", () -> "localhost");
    registry.add(auth + ".port", () -> AUTH_SERVICE.getAddress().getPort());
    registry.add(md + ".host", () -> "localhost");
    registry.add(md + ".port", () -> MASTERDATA_SERVICE.getAddress().getPort());
  }

  @AfterAll
  static void stop() {
    AUTH_SERVICE.stop(0);
    MASTERDATA_SERVICE.stop(0);
  }

  private static String token(String issuer, Instant expiresAt, String secret) {
    NimbusJwtEncoder encoder =
        new NimbusJwtEncoder(
            new ImmutableSecret<>(
                new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256")));
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("bob")
            .issuedAt(expiresAt.minusSeconds(3600))
            .expiresAt(expiresAt)
            .claim("userId", 7L)
            .claim("userType", 2)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  private WebTestClient.ResponseSpec get(String bearer) {
    WebTestClient.RequestHeadersSpec<?> req = client.get().uri("/api/products");
    return (bearer == null ? req : req.header("Authorization", "Bearer " + bearer)).exchange();
  }

  @Test
  void loginRouteIsPublicAndReachesAuthService() {
    client
        .post()
        .uri("/api/auth/login")
        .bodyValue("{}")
        .header("Content-Type", "application/json")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.token")
        .isEqualTo("t");
  }

  @Test
  void masterdataRouteWithTokenReachesMasterdata() {
    String token = token(ISSUER, Instant.now().plusSeconds(600), SECRET);
    client
        .get()
        .uri("/api/products")
        .header("Authorization", "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("7");
  }

  @Test
  void userInfoCreateIsPublic() {
    client
        .post()
        .uri("/api/user-info")
        .header("Content-Type", "application/json")
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void userInfoUpdateRequiresToken() {
    client
        .put()
        .uri("/api/user-info/1")
        .header("Content-Type", "application/json")
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void userInfoUpdateWithTokenReachesAuthService() {
    String token = token(ISSUER, Instant.now().plusSeconds(600), SECRET);
    client
        .put()
        .uri("/api/user-info/1")
        .header("Authorization", "Bearer " + token)
        .header("Content-Type", "application/json")
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk();
  }

  @Test
  void otherUserInfoApisAreBlockedEvenWithToken() {
    String token = token(ISSUER, Instant.now().plusSeconds(600), SECRET);
    for (String uri : new String[] {"/api/user-info", "/api/user-info/1", "/api/user-info/loginId/bob"}) {
      client
          .get()
          .uri(uri)
          .header("Authorization", "Bearer " + token)
          .exchange()
          .expectStatus()
          .isForbidden();
    }
  }

  @Test
  void masterdataRouteRequiresToken() {
    get(null).expectStatus().isUnauthorized();
  }

  @Test
  void validTokenReachesMasterdataWithUserHeaders() {
    String token = token(ISSUER, Instant.now().plusSeconds(600), SECRET);
    get(token)
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .isEqualTo("7")
        .jsonPath("$.role")
        .isEqualTo("CUSTOMER");
  }

  @Test
  void clientSuppliedIdentityHeadersAreReplaced() {
    String token = token(ISSUER, Instant.now().plusSeconds(600), SECRET);
    client
        .get()
        .uri("/api/products")
        .header("Authorization", "Bearer " + token)
        .header("X-User-Role", "ADMIN")
        .exchange()
        .expectBody()
        .jsonPath("$.role")
        .isEqualTo("CUSTOMER");
  }

  @Test
  void expiredTokenRejected() {
    get(token(ISSUER, Instant.now().minusSeconds(600), SECRET)).expectStatus().isUnauthorized();
  }

  @Test
  void wrongIssuerRejected() {
    get(token("someone-else", Instant.now().plusSeconds(600), SECRET))
        .expectStatus()
        .isUnauthorized();
  }

  @Test
  void wrongSignatureRejected() {
    String other = Base64.getEncoder().encodeToString("another-secret-another-secret-123".getBytes());
    get(token(ISSUER, Instant.now().plusSeconds(600), other)).expectStatus().isUnauthorized();
  }

  @Test
  void swaggerUiIsPublic() {
    client.get().uri("/swagger-ui.html").exchange().expectStatus().is3xxRedirection();
  }

  @Test
  void userauthApiDocsAreRoutedWithoutToken() {
    client
        .get()
        .uri("/docs/userauth/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.token")
        .exists();
  }

  @Test
  void masterdataApiDocsAreRoutedWithoutToken() {
    client
        .get()
        .uri("/docs/masterdata/api-docs")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.id")
        .exists();
  }

  @Test
  void rolesMapAllTypes() {
    assertThat(
            com.enncompany.ecomgatewayservice.auth.Roles.of(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                    .header("alg", "none")
                    .claim("userType", 0)
                    .build()))
        .isEqualTo("ADMIN");
    assertThat(
            com.enncompany.ecomgatewayservice.auth.Roles.of(
                org.springframework.security.oauth2.jwt.Jwt.withTokenValue("t")
                    .header("alg", "none")
                    .claim("userType", 9)
                    .build()))
        .isEqualTo("UNKNOWN");
  }
}
