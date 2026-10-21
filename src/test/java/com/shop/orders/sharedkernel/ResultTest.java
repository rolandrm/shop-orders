package com.shop.orders.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.sharedkernel.DomainError.Kind;
import com.shop.orders.sharedkernel.Result.Failure;
import com.shop.orders.sharedkernel.Result.Success;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResultTest {

  private final DomainError error = new DomainError(Kind.NOT_FOUND, "X", "absent");

  @Test
  void map_on_success_transforms_value() {
    assertThat(new Success<>(2).map(v -> v * 3)).isEqualTo(new Success<>(6));
  }

  @Test
  void map_on_failure_keeps_error_without_calling_function() {
    AtomicInteger calls = new AtomicInteger();
    Result<Integer> r =
        new Failure<Integer>(error)
            .map(
                v -> {
                  calls.incrementAndGet();
                  return v;
                });
    assertThat(r).isEqualTo(new Failure<Integer>(error));
    assertThat(calls).hasValue(0);
  }

  @Test
  void flatMap_on_success_returns_function_result() {
    Result<Integer> ok = new Success<>(2);
    assertThat(ok.flatMap(v -> new Success<>(v + 1))).isEqualTo(new Success<>(3));
    assertThat(ok.flatMap(v -> new Failure<Integer>(error))).isEqualTo(new Failure<Integer>(error));
  }

  @Test
  void flatMap_on_failure_does_not_call_function() {
    AtomicInteger calls = new AtomicInteger();
    Result<Integer> r =
        new Failure<Integer>(error)
            .flatMap(
                v -> {
                  calls.incrementAndGet();
                  return new Success<>(v);
                });
    assertThat(r).isEqualTo(new Failure<Integer>(error));
    assertThat(calls).hasValue(0);
  }

  @Test
  void fold_picks_the_matching_branch() {
    Result<Integer> ok = new Success<>(2);
    Result<Integer> ko = new Failure<>(error);
    String fromOk = ok.<String>fold(v -> "ok" + v, e -> "ko");
    String fromKo = ko.<String>fold(v -> "ok", e -> "ko " + e.code());
    assertThat(fromOk).isEqualTo("ok2");
    assertThat(fromKo).isEqualTo("ko X");
  }
}
