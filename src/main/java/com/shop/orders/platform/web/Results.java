package com.shop.orders.platform.web;

import com.shop.orders.sharedkernel.Result;

/** Utilitaires de passage de {@link Result} vers la couche web. */
public final class Results {

  private Results() {}

  /** Renvoie la valeur d'un succès, ou lève {@link DomainErrorException} pour un échec. */
  public static <T> T unwrap(Result<T> result) {
    return switch (result) {
      case Result.Success<T> s -> s.value();
      case Result.Failure<T> f -> throw new DomainErrorException(f.error());
    };
  }
}
