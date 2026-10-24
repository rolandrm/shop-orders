package com.shop.orders.orders.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Projection de la liste : aucune ligne n'est chargée. */
record OrderSummaryRow(
    UUID id, Instant createdAt, String currency, BigDecimal totalAmount, int itemCount) {}
