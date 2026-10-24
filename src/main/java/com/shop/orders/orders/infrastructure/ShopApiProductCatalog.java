package com.shop.orders.orders.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Adaptateur du port {@link ProductCatalog} : {@code GET /v1/products/{id}} sur shop-api. Seul un
 * {@code 200} lisible et valide (nom de 1 à 200 caractères, non blanc) donne un produit ; un nom
 * vide, blanc ou de plus de 200 caractères donne « indisponible » ; {@code 404} donne « inconnu » ;
 * tout le reste (autre statut, corps illisible, délai, connexion) donne « indisponible ». Seuls le
 * statut ou le type d'erreur sont journalisés, jamais le corps.
 */
@Component
class ShopApiProductCatalog implements ProductCatalog {

  private static final Logger LOG = LoggerFactory.getLogger(ShopApiProductCatalog.class);

  private final RestClient client;
  private final Validator validator;

  ShopApiProductCatalog(RestClient shopApiRestClient, Validator validator) {
    this.client = shopApiRestClient;
    this.validator = validator;
  }

  @Override
  public Result<CatalogProduct> find(ProductId id) {
    try {
      return client
          .get()
          .uri("/v1/products/{id}", id.value().toString())
          .accept(MediaType.APPLICATION_JSON)
          .exchange((request, response) -> translate(response.getStatusCode().value(), response));
    } catch (RestClientException | HttpMessageConversionException | IllegalArgumentException e) {
      LOG.warn("Lecture du produit dans shop-api impossible : {}", e.getClass().getSimpleName());
      return new Result.Failure<>(OrderErrors.catalogUnavailable());
    }
  }

  private Result<CatalogProduct> translate(
      int status, RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    if (status == 404) {
      return new Result.Failure<>(OrderErrors.productNotFound());
    }
    if (status != 200) {
      LOG.warn("shop-api a répondu avec le statut {}", status);
      return new Result.Failure<>(OrderErrors.catalogUnavailable());
    }
    ProductResponse body = response.bodyTo(ProductResponse.class);
    if (body == null || !validator.validate(body).isEmpty()) {
      LOG.warn("Réponse de shop-api illisible ou hors contrat");
      return new Result.Failure<>(OrderErrors.catalogUnavailable());
    }
    return new Result.Success<>(body.toDomain());
  }

  /** Réponse {@code 200} de shop-api ; les champs inconnus sont ignorés. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record ProductResponse(
      @NotNull UUID id,
      @NotBlank @Size(max = 200) String name,
      @NotNull @Valid PriceResponse price,
      @NotNull @Min(0) Integer stock) {

    CatalogProduct toDomain() {
      return new CatalogProduct(
          new ProductId(id), name, Money.of(price.amount(), price.currency()), stock);
    }
  }

  /** Prix d'un produit : montant en chaîne à deux décimales, devise ISO 4217. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  record PriceResponse(
      @NotNull @Pattern(regexp = "^\\d+\\.\\d{2}$") String amount,
      @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {}
}
