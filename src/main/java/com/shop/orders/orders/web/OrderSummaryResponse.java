package com.shop.orders.orders.web;

import com.shop.orders.orders.domain.OrderSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/** Résumé d'une commande dans la liste. */
@Schema(description = "Résumé d'une commande dans la liste, sans ses lignes.")
record OrderSummaryResponse(
    @Schema(
            description = "Identifiant de la commande.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
    @Schema(
            description = "Date de création de la commande, ISO 8601 en UTC.",
            example = "2026-10-22T10:15:30Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
    @Schema(
            description = "Nombre de lignes de la commande.",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int itemCount,
    @Schema(description = "Total de la commande.", requiredMode = Schema.RequiredMode.REQUIRED)
        MoneyResponse total) {

  static OrderSummaryResponse from(OrderSummary summary) {
    return new OrderSummaryResponse(
        summary.id().value(),
        summary.createdAt(),
        summary.itemCount(),
        MoneyResponse.from(summary.total()));
  }
}
