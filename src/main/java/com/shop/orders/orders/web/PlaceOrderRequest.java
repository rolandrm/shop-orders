package com.shop.orders.orders.web;

import com.shop.orders.orders.application.PlaceOrderCommand;
import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.orders.domain.RequestedItem;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Demande de commande : produits et quantités, jamais de prix. */
@Schema(description = "Demande de commande : les produits et les quantités voulus, sans prix.")
record PlaceOrderRequest(
    @Schema(
            description = "Lignes demandées, de 1 à 50, un produit au plus une fois.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(min = 1, max = 50)
        List<@Valid @NotNull Item> items) {

  /** Une ligne demandée. */
  @Schema(name = "PlaceOrderItemRequest", description = "Un produit et la quantité voulue.")
  record Item(
      @Schema(
              description = "Identifiant du produit dans le catalogue.",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull
          UUID productId,
      @Schema(
              description = "Quantité voulue, de 1 à 100.",
              example = "2",
              minimum = "1",
              maximum = "100",
              requiredMode = Schema.RequiredMode.REQUIRED)
          @NotNull
          @Min(1)
          @Max(100)
          Integer quantity) {}

  PlaceOrderCommand toCommand(UUID customerId) {
    List<RequestedItem> requested =
        items.stream()
            .map(i -> new RequestedItem(new ProductId(i.productId()), i.quantity()))
            .toList();
    return new PlaceOrderCommand(new CustomerId(customerId), requested);
  }
}
