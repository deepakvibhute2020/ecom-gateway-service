package com.enncompany.ecomgatewayservice.auth;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Passes the authenticated identity downstream and drops any client-supplied identity headers. */
@Component
public class UserContextHeaderFilter implements GlobalFilter, Ordered {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    return ReactiveSecurityContextHolder.getContext()
        .map(ctx -> ctx.getAuthentication())
        .filter(JwtAuthenticationToken.class::isInstance)
        .map(a -> ((JwtAuthenticationToken) a).getToken())
        .map(jwt -> rewrite(exchange, jwt))
        .defaultIfEmpty(rewrite(exchange, null))
        .flatMap(chain::filter);
  }

  private static ServerWebExchange rewrite(ServerWebExchange exchange, Jwt jwt) {
    return exchange
        .mutate()
        .request(
            r ->
                r.headers(
                    h -> {
                      h.remove("X-User-Id");
                      h.remove("X-User-Role");
                      if (jwt != null) {
                        h.set("X-User-Id", String.valueOf(jwt.getClaims().get("userId")));
                        h.set("X-User-Role", Roles.of(jwt));
                      }
                    }))
        .build();
  }

  @Override
  public int getOrder() {
    return Ordered.HIGHEST_PRECEDENCE + 10;
  }
}
