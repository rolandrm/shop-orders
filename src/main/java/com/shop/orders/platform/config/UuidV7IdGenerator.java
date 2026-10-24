package com.shop.orders.platform.config;

import com.shop.orders.sharedkernel.port.IdGenerator;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Identifiants UUID version 7 (RFC 9562) : 48 bits d'horodatage en millisecondes, puis aléa. */
@Component
public class UuidV7IdGenerator implements IdGenerator {

  private final Clock clock;
  private final SecureRandom random = new SecureRandom();

  public UuidV7IdGenerator(Clock clock) {
    this.clock = clock;
  }

  @Override
  public UUID next() {
    long millis = clock.millis();
    long randA = random.nextLong() & 0x0FFFL;
    long msb = (millis << 16) | 0x7000L | randA;
    long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
    return new UUID(msb, lsb);
  }
}
