package com.shop.orders.platform.web;

import com.shop.orders.sharedkernel.DomainError;

/** Porte une {@link DomainError} jusqu'au gestionnaire d'exceptions global. */
public class DomainErrorException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient DomainError error;

  public DomainErrorException(DomainError error) {
    super(error.code() + ": " + error.message());
    this.error = error;
  }

  public DomainError error() {
    return error;
  }
}
