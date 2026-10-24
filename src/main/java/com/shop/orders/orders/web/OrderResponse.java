package com.shop.orders.orders.web;

import com.shop.orders.orders.domain.Order;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Commande complète. */
@Schema(description = "Commande d'un client, avec ses lignes et son total.")
record OrderResponse(
    @Schema(
            description = "Identifiant de la commande.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID id,
    @Schema(
            description = "Identifiant du client propriétaire de la commande.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID customerId,
    @Schema(
            description = "Date de création de la commande, ISO 8601 en UTC.",
            example = "2026-10-22T10:15:30Z",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Instant createdAt,
    @Schema(
            description = "Lignes de la commande, de 1 à 50, dans l'ordre de la demande.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<OrderLineResponse> items,
    @Schema(
            description =
                "Total de la commande : somme des totaux de ligne, dans une seule devise.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        MoneyResponse total) {

  static OrderResponse from(Order order) {
    return new OrderResponse(
        order.id().value(),
        order.customerId().value(),
        order.createdAt(),
        order.lines().stream().map(OrderLineResponse::from).toList(),
        MoneyResponse.from(order.total()));
  }
}
