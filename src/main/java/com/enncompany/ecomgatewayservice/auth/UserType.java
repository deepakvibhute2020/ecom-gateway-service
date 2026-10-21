package com.enncompany.ecomgatewayservice.auth;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Mirrors the user-service enum, which serializes as its numeric code. */
public enum UserType {
  ADMIN,
  SUPPLIER,
  CUSTOMER;

  @JsonCreator
  public static UserType fromCode(int code) {
    return values()[code];
  }
}
