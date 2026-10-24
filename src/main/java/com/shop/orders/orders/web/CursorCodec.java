package com.shop.orders.orders.web;

import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.sharedkernel.Result;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

/** Encode le curseur de pagination en texte opaque (base64url) et le décode. */
final class CursorCodec {

  private static final String SEPARATOR = "|";

  private CursorCodec() {}

  static String encode(OrderCursor cursor) {
    String raw = cursor.createdAt() + SEPARATOR + cursor.id().value();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }

  /** Décode un curseur ; un texte illisible donne un échec {@code VALIDATION_ERROR}. */
  static Result<OrderCursor> decode(String text) {
    try {
      String raw = new String(Base64.getUrlDecoder().decode(text), StandardCharsets.UTF_8);
      int separator = raw.indexOf(SEPARATOR);
      if (separator < 0) {
        return invalid();
      }
      Instant createdAt = Instant.parse(raw.substring(0, separator));
      UUID id = UUID.fromString(raw.substring(separator + 1));
      return new Result.Success<>(new OrderCursor(createdAt, new OrderId(id)));
    } catch (IllegalArgumentException | DateTimeParseException e) {
      return invalid();
    }
  }

  private static Result<OrderCursor> invalid() {
    return new Result.Failure<>(OrderErrors.validation("Le curseur est illisible"));
  }
}
