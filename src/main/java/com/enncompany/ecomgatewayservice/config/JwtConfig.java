package com.enncompany.ecomgatewayservice.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

  private static SecretKey key(JwtProperties props) {
    byte[] bytes = Base64.getDecoder().decode(props.secret());
    if (bytes.length < 32) {
      throw new IllegalStateException("app.jwt.secret must be Base64 of at least 32 bytes");
    }
    return new SecretKeySpec(bytes, "HmacSHA256");
  }

  /** Validates signature, expiry and issuer of tokens issued by the auth service. */
  @Bean
  public ReactiveJwtDecoder jwtDecoder(JwtProperties props) {
    NimbusReactiveJwtDecoder decoder =
        NimbusReactiveJwtDecoder.withSecretKey(key(props)).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(props.issuer()));
    return decoder;
  }
}
