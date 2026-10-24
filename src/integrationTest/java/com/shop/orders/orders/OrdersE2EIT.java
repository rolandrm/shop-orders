package com.shop.orders.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.shop.orders.PostgresContainer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Bout en bout : application complète sur un PostgreSQL réel (schéma par Flyway), port aléatoire.
 * Le client de shop-api est remplacé par un client lié à {@link MockRestServiceServer} : aucun
 * appel réseau.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.autoconfigure.exclude=",
      "spring.flyway.enabled=true",
      "APP_SERVICE_NAME=shop-orders",
      "SHOP_API_BASE_URL=http://shop-api.test.invalid",
      "SPRING_DATASOURCE_URL=jdbc:postgresql://unused.invalid:5432/orders",
      "SPRING_DATASOURCE_USERNAME=orders",
      "SPRING_DATASOURCE_PASSWORD=not-a-real-password"
    })
@Import({PostgresContainer.class, OrdersE2EIT.FakeShopApi.class})
class OrdersE2EIT {

  private static final String SHOP_API = "http://shop-api.test.invalid";
  private static final String PRODUCT = "018f0000-0000-7000-8000-000000000001";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Faux shop-api : client prioritaire lié à un serveur simulé, avec un compteur de requêtes. */
  @TestConfiguration(proxyBeanMethods = false)
  static class FakeShopApi {

    private final AtomicInteger requests = new AtomicInteger();
    private final RestClient.Builder builder =
        RestClient.builder()
            .baseUrl(SHOP_API)
            .requestInterceptor(
                (request, body, execution) -> {
                  requests.incrementAndGet();
                  return execution.execute(request, body);
                });
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Bean
    @Primary
    RestClient fakeShopApiRestClient() {
      return builder.build();
    }

    @Bean
    MockRestServiceServer shopApiServer() {
      return server;
    }

    @Bean
    AtomicInteger shopApiRequests() {
      return requests;
    }
  }

  @LocalServerPort int port;

  @Autowired MockRestServiceServer shopApi;

  @Autowired AtomicInteger shopApiRequests;

  private static String product(String price) {
    return "{\"id\":\""
        + PRODUCT
        + "\",\"name\":\"Clavier\",\"price\":{\"amount\":\""
        + price
        + "\",\"currency\":\"EUR\"},\"stock\":50}";
  }

  private RestClient app() {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build();
  }

  private static JsonNode body(ResponseEntity<String> response) {
    return JSON.readTree(response.getBody());
  }

  @Test
  void orderKeepsItsPricesWhenTheCatalogPriceChangesAndReadingNeverCallsShopApi() {
    String customer = UUID.randomUUID().toString();

    // création : le faux shop-api annonce 10.00
    shopApi
        .expect(once(), requestTo(SHOP_API + "/v1/products/" + PRODUCT))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withSuccess(product("10.00"), MediaType.APPLICATION_JSON));
    ResponseEntity<String> created =
        app()
            .post()
            .uri("/v1/orders")
            .header("X-Customer-Id", customer)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"items\":[{\"productId\":\"" + PRODUCT + "\",\"quantity\":2}]}")
            .retrieve()
            .toEntity(String.class);

    assertThat(created.getStatusCode().value()).isEqualTo(201);
    String id = body(created).get("id").asString();
    assertThat(created.getHeaders().getFirst("Location")).isEqualTo("/v1/orders/" + id);
    assertThat(body(created).get("total").get("amount").asString()).isEqualTo("20.00");
    shopApi.verify();
    assertThat(shopApiRequests.get()).isEqualTo(1);

    // le prix annoncé change : toute requête vers shop-api échouerait désormais
    shopApi.reset();
    shopApi
        .expect(requestTo(SHOP_API + "/v1/products/" + PRODUCT))
        .andRespond(withSuccess(product("99.00"), MediaType.APPLICATION_JSON));

    ResponseEntity<String> read =
        app()
            .get()
            .uri("/v1/orders/{id}", id)
            .header("X-Customer-Id", customer)
            .retrieve()
            .toEntity(String.class);

    assertThat(read.getStatusCode().value()).isEqualTo(200);
    JsonNode order = body(read);
    assertThat(order.get("items").get(0).get("unitPrice").get("amount").asString())
        .isEqualTo("10.00");
    assertThat(order.get("items").get(0).get("lineTotal").get("amount").asString())
        .isEqualTo("20.00");
    assertThat(order.get("total").get("amount").asString()).isEqualTo("20.00");
    assertThat(shopApiRequests.get())
        .as("aucune requête vers shop-api pendant la lecture")
        .isEqualTo(1);

    // la liste renvoie la commande, sans curseur suivant
    ResponseEntity<String> list =
        app()
            .get()
            .uri("/v1/orders")
            .header("X-Customer-Id", customer)
            .retrieve()
            .toEntity(String.class);

    assertThat(list.getStatusCode().value()).isEqualTo(200);
    JsonNode page = body(list);
    assertThat(page.get("items")).hasSize(1);
    assertThat(page.get("items").get(0).get("id").asString()).isEqualTo(id);
    assertThat(page.get("items").get(0).get("itemCount").asInt()).isEqualTo(1);
    assertThat(page.get("items").get(0).get("total").get("amount").asString()).isEqualTo("20.00");
    assertThat(page.has("nextCursor")).isFalse();
    assertThat(shopApiRequests.get()).isEqualTo(1);
  }
}
