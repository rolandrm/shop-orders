package com.shop.orders.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * T9 : ce que l'adaptateur lit et traduit existe dans le contrat de shop-api (lecture seule de
 * {@code docs/api/backends/shop-api.openapi.json}).
 */
class ShopApiContractConformanceIT {

  private static final Path CONTRACT = Path.of("docs/api/backends/shop-api.openapi.json");
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private static JsonNode getOperation;

  @BeforeAll
  static void readContract() throws IOException {
    JsonNode root = MAPPER.readTree(Files.readString(CONTRACT));
    getOperation = root.path("paths").path("/v1/products/{id}").path("get");
  }

  @Test
  void productPathWithGetExists() {
    assertThat(getOperation.isObject()).as("GET /v1/products/{id} dans le contrat").isTrue();
  }

  @Test
  void responsesDeclareTheTranslatedStatuses() {
    JsonNode responses = getOperation.path("responses");

    assertThat(responses.propertyNames()).contains("200", "400", "404", "500");
  }

  @Test
  void the200SchemaDeclaresTheFieldsTheAdapterReads() {
    JsonNode schema = successSchema();

    assertThat(schema.path("properties").propertyNames()).contains("id", "name", "price", "stock");
    assertThat(schema.path("properties").path("price").path("properties").propertyNames())
        .contains("amount", "currency");
  }

  @Test
  void every200FixtureCarriesTheDeclaredFields() throws IOException {
    for (String name : List.of("product-200.json", "product-200-extra-field.json")) {
      try (InputStream in = getClass().getResourceAsStream("/shop-api/" + name)) {
        assertThat(in).as(name).isNotNull();
        JsonNode body = MAPPER.readTree(in);
        assertThat(body.has("id")).as(name + " id").isTrue();
        assertThat(body.has("name")).as(name + " name").isTrue();
        assertThat(body.path("price").has("amount")).as(name + " price.amount").isTrue();
        assertThat(body.path("price").has("currency")).as(name + " price.currency").isTrue();
        assertThat(body.has("stock")).as(name + " stock").isTrue();
      }
    }
  }

  @Test
  void nameBoundOfTheAdapterEqualsTheMaxLengthOfTheContract() throws NoSuchFieldException {
    int declared = successSchema().path("properties").path("name").path("maxLength").asInt(-1);
    Size size =
        ShopApiProductCatalog.ProductResponse.class
            .getDeclaredField("name")
            .getAnnotation(Size.class);

    assertThat(declared).as("maxLength de name dans le contrat").isEqualTo(200);
    assertThat(size).as("@Size sur le champ name de ProductResponse").isNotNull();
    assertThat(size.max()).isEqualTo(declared);
  }

  private static JsonNode successSchema() {
    return getOperation
        .path("responses")
        .path("200")
        .path("content")
        .path("application/json")
        .path("schema");
  }
}
