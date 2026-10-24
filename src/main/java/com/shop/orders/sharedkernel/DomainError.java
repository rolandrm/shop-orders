package com.shop.orders.sharedkernel;

/** Erreur métier stable : un genre, un code et un message. */
public record DomainError(Kind kind, String code, String message) {

  /** Genre d'erreur métier. */
  public enum Kind {
    VALIDATION,
    NOT_FOUND,
    CONFLICT,
    UNAVAILABLE,
    UNEXPECTED
  }
}
