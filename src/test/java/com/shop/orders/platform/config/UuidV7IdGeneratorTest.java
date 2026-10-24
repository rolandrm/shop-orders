package com.shop.orders.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7IdGeneratorTest {

  private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

  @Test
  void idsAreVersion7WithRfc9562Variant() {
    UuidV7IdGenerator generator = new UuidV7IdGenerator(Clock.fixed(START, ZoneOffset.UTC));
    UUID id = generator.next();
    assertThat(id.version()).isEqualTo(7);
    assertThat(id.variant()).isEqualTo(2);
  }

  @Test
  void thousandIdsAreDistinct() {
    UuidV7IdGenerator generator = new UuidV7IdGenerator(Clock.systemUTC());
    Set<UUID> ids = new HashSet<>();
    for (int i = 0; i < 1000; i++) {
      ids.add(generator.next());
    }
    assertThat(ids).hasSize(1000);
  }

  @Test
  void lexicographicOrderFollowsTime() {
    Clock first = Clock.fixed(START, ZoneOffset.UTC);
    Clock second = Clock.offset(first, Duration.ofMillis(1));
    String before = new UuidV7IdGenerator(first).next().toString();
    String after = new UuidV7IdGenerator(second).next().toString();
    assertThat(before).isLessThan(after);
  }
}
