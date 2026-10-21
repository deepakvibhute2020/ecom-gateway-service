package com.enncompany.ecomgatewayservice.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** Maps the auth service's numeric userType claim (0 ADMIN, 1 SUPPLIER, 2 CUSTOMER) to a role. */
public final class Roles {

  private static final String[] NAMES = {"ADMIN", "SUPPLIER", "CUSTOMER"};

  private Roles() {}

  public static String of(Jwt jwt) {
    Object type = jwt.getClaims().get("userType");
    if (type instanceof Number n && n.intValue() >= 0 && n.intValue() < NAMES.length) {
      return NAMES[n.intValue()];
    }
    return "UNKNOWN";
  }
}
