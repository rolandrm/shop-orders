package com.shop.orders.orders.web;

import com.shop.orders.orders.domain.OrderLine;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/** Ligne d'une commande, telle que figée à l'achat. */
@Schema(description = "Ligne d'une commande : nom et prix du produit figés au moment de l'achat.")
record OrderLineResponse(
    @Schema(
            description = "Identifiant du produit dans le catalogue.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        UUID productId,
    @Schema(
            description = "Nom du produit copié du catalogue à la création de la commande.",
            example = "Clavier",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String productName,
    @Schema(
            description = "Prix unitaire copié du catalogue à la création de la commande.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        MoneyResponse unitPrice,
    @Schema(
            description = "Quantité commandée, de 1 à 100.",
            example = "2",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int quantity,
    @Schema(
            description = "Total de la ligne : prix unitaire multiplié par la quantité.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        MoneyResponse lineTotal) {

  static OrderLineResponse from(OrderLine line) {
    return new OrderLineResponse(
        line.productId().value(),
        line.productName(),
        MoneyResponse.from(line.unitPrice()),
        line.quantity(),
        MoneyResponse.from(line.lineTotal()));
  }
}
