package com.shop.orders.sharedkernel.port;

import java.util.UUID;

/** Port : génère des identifiants uniques. */
public interface IdGenerator {

  UUID next();
}
