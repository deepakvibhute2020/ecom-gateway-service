package com.enncompany.ecomgatewayservice.auth;

import com.enncompany.ecomgatewayservice.config.JwtProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/auth")
public class AuthController {

  public record LoginRequest(@NotBlank String loginId, @NotBlank String password) {}

  public record UserVerification(Long userId, String loginId, UserType userType) {}

  public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}

  private final WebClient userService;
  private final JwtEncoder encoder;
  private final JwtProperties props;

  public AuthController(WebClient userServiceClient, JwtEncoder encoder, JwtProperties props) {
    this.userService = userServiceClient;
    this.encoder = encoder;
    this.props = props;
  }

  @PostMapping("/login")
  public Mono<ResponseEntity<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
    return userService
        .post()
        .uri("/internal/auth/verify")
        .bodyValue(request)
        .retrieve()
        .bodyToMono(UserVerification.class)
        .map(user -> ResponseEntity.ok(issue(user)))
        .onErrorResume(
            WebClientResponseException.Unauthorized.class,
            e -> Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()));
  }

  private TokenResponse issue(UserVerification user) {
    Instant now = Instant.now();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(props.issuer())
            .issuedAt(now)
            .expiresAt(now.plus(props.ttl()))
            .subject(user.loginId())
            .claim("uid", user.userId())
            .claim("role", user.userType().name())
            .build();
    String token =
        encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
    return new TokenResponse(token, "Bearer", props.ttl().toSeconds());
  }
}
