package com.shop.orders.sharedkernel;

import java.util.function.Function;

/** Résultat d'une opération : succès portant une valeur, ou échec portant une erreur métier. */
public sealed interface Result<T> {

  /** Succès. */
  record Success<T>(T value) implements Result<T> {}

  /** Échec. */
  record Failure<T>(DomainError error) implements Result<T> {}

  default <U> Result<U> map(Function<? super T, ? extends U> fn) {
    return switch (this) {
      case Success<T> s -> new Success<U>(fn.apply(s.value()));
      case Failure<T> f -> new Failure<U>(f.error());
    };
  }

  default <U> Result<U> flatMap(Function<? super T, ? extends Result<U>> fn) {
    return switch (this) {
      case Success<T> s -> fn.apply(s.value());
      case Failure<T> f -> new Failure<U>(f.error());
    };
  }

  default <U> U fold(
      Function<? super T, ? extends U> onSuccess,
      Function<? super DomainError, ? extends U> onFailure) {
    return switch (this) {
      case Success<T> s -> onSuccess.apply(s.value());
      case Failure<T> f -> onFailure.apply(f.error());
    };
  }
}
