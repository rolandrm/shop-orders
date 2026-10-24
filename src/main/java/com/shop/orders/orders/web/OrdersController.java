package com.shop.orders.orders.web;

import com.shop.orders.orders.application.GetOrder;
import com.shop.orders.orders.application.ListOrders;
import com.shop.orders.orders.application.OrderPage;
import com.shop.orders.orders.application.PlaceOrder;
import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.platform.web.Results;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints des commandes. Les paramètres bornés portent leurs contraintes sans {@code @Validated}
 * de classe : la validation de méthode de Spring MVC lève alors une exception de requête (400) et
 * non une exception d'AOP (500).
 */
@RestController
@RequestMapping("/v1/orders")
@Tag(name = "Commandes", description = "Passer, relire et lister les commandes d'un client.")
class OrdersController {

  private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
  private static final String API_ERROR = "#/components/schemas/ApiError";

  private final PlaceOrder placeOrder;
  private final GetOrder getOrder;
  private final ListOrders listOrders;

  OrdersController(PlaceOrder placeOrder, GetOrder getOrder, ListOrders listOrders) {
    this.placeOrder = placeOrder;
    this.getOrder = getOrder;
    this.listOrders = listOrders;
  }

  @PostMapping
  @Operation(
      summary = "Passer une commande",
      requestBody =
          @io.swagger.v3.oas.annotations.parameters.RequestBody(
              description = "Produits et quantités voulus, sans prix : le catalogue fait foi.",
              required = true),
      description =
          "Crée la commande du client à partir de produits et de quantités. Le nom et le prix de"
              + " chaque produit sont lus dans le catalogue et figés dans la commande.")
  @ApiResponse(
      responseCode = "201",
      description = "Commande créée ; l'en-tête Location donne son adresse.",
      content = @Content(mediaType = JSON, schema = @Schema(implementation = OrderResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description =
          "Requête invalide (VALIDATION_ERROR : corps, en-tête) ou produit inconnu"
              + " (PRODUCT_NOT_FOUND).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  @ApiResponse(
      responseCode = "409",
      description =
          "Stock insuffisant (INSUFFICIENT_STOCK) ou devises différentes (MIXED_CURRENCIES).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  @ApiResponse(
      responseCode = "500",
      description = "Erreur interne (INTERNAL_ERROR).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  @ApiResponse(
      responseCode = "503",
      description = "Catalogue indisponible (CATALOG_UNAVAILABLE) ; réessayer plus tard.",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  ResponseEntity<OrderResponse> place(
      @RequestHeader("X-Customer-Id")
          @Parameter(
              description = "Identifiant du client (UUID) pour le compte duquel on agit.",
              required = true)
          UUID customerId,
      @Valid @RequestBody PlaceOrderRequest request) {
    Order order = Results.unwrap(placeOrder.execute(request.toCommand(customerId)));
    return ResponseEntity.created(URI.create("/v1/orders/" + order.id().value()))
        .body(OrderResponse.from(order));
  }

  @GetMapping("/{orderId}")
  @Operation(
      summary = "Lire une commande",
      description =
          "Renvoie une commande du client avec les prix figés à l'achat, sans consulter le"
              + " catalogue.")
  @ApiResponse(
      responseCode = "200",
      description = "La commande demandée.",
      content = @Content(mediaType = JSON, schema = @Schema(implementation = OrderResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "Identifiant ou en-tête invalide (VALIDATION_ERROR).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  @ApiResponse(
      responseCode = "404",
      description = "Commande introuvable (ORDER_NOT_FOUND), y compris celle d'un autre client.",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  @ApiResponse(
      responseCode = "500",
      description = "Erreur interne (INTERNAL_ERROR).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  OrderResponse get(
      @RequestHeader("X-Customer-Id")
          @Parameter(
              description = "Identifiant du client (UUID) propriétaire de la commande.",
              required = true)
          UUID customerId,
      @PathVariable("orderId") @Parameter(description = "Identifiant de la commande à lire (UUID).")
          UUID orderId) {
    return OrderResponse.from(
        Results.unwrap(getOrder.execute(new OrderId(orderId), new CustomerId(customerId))));
  }

  @GetMapping
  @Operation(
      summary = "Lister les commandes d'un client",
      description =
          "Renvoie une page des commandes du client, les plus récentes d'abord, avec un curseur"
              + " pour la page suivante.")
  @ApiResponse(
      responseCode = "200",
      description = "Une page de commandes ; nextCursor est absent sur la dernière page.",
      content =
          @Content(mediaType = JSON, schema = @Schema(implementation = OrderListResponse.class)))
  @ApiResponse(
      responseCode = "400",
      description = "En-tête, limit ou cursor invalide (VALIDATION_ERROR).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  @ApiResponse(
      responseCode = "500",
      description = "Erreur interne (INTERNAL_ERROR).",
      content = @Content(mediaType = JSON, schema = @Schema(ref = API_ERROR)))
  OrderListResponse list(
      @RequestHeader("X-Customer-Id")
          @Parameter(
              description = "Identifiant du client (UUID) dont on liste les commandes.",
              required = true)
          UUID customerId,
      @RequestParam(name = "limit", defaultValue = "20")
          @Min(1)
          @Max(100)
          @Parameter(description = "Nombre maximal de commandes dans la page, de 1 à 100.")
          int limit,
      @RequestParam(name = "cursor", required = false)
          @Parameter(
              description =
                  "Curseur opaque issu du nextCursor de la page précédente ; absent pour la"
                      + " première page.")
          @Nullable String cursor) {
    OrderCursor after = cursor == null ? null : Results.unwrap(CursorCodec.decode(cursor));
    OrderPage page = Results.unwrap(listOrders.execute(new CustomerId(customerId), after, limit));
    OrderCursor next = page.nextCursor();
    return new OrderListResponse(
        page.items().stream().map(OrderSummaryResponse::from).toList(),
        next == null ? null : CursorCodec.encode(next));
  }
}
