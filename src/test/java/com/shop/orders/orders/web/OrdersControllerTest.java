package com.shop.orders.orders.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.application.GetOrder;
import com.shop.orders.orders.application.ListOrders;
import com.shop.orders.orders.application.PlaceOrder;
import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.CustomerId;
import com.shop.orders.orders.domain.Order;
import com.shop.orders.orders.domain.OrderCursor;
import com.shop.orders.orders.domain.OrderId;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.OrderSummary;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.orders.fakes.FakeProductCatalog;
import com.shop.orders.orders.fakes.InMemoryOrderRepository;
import com.shop.orders.platform.web.GlobalExceptionHandler;
import com.shop.orders.platform.web.RequestIdFilter;
import com.shop.orders.sharedkernel.Money;
import com.shop.orders.sharedkernel.Result;
import com.shop.orders.sharedkernel.port.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Controller des commandes : vrais cas d'usage, fakes en mémoire, validation Spring MVC réelle. */
@WebMvcTest(controllers = OrdersController.class)
@Import({GlobalExceptionHandler.class, RequestIdFilter.class})
class OrdersControllerTest {

  private static final String CUSTOMER = "00000000-0000-7000-8000-0000000000c1";
  private static final String OTHER_CUSTOMER = "00000000-0000-7000-8000-0000000000c2";
  private static final ProductId P1 = new ProductId(new UUID(3, 1));
  private static final ProductId P2 = new ProductId(new UUID(3, 2));
  private static final ProductId USD_PRODUCT = new ProductId(new UUID(3, 3));
  private static final ProductId LAST_ONE = new ProductId(new UUID(3, 4));
  private static final JsonMapper JSON = JsonMapper.builder().build();

  /** Fakes remis à zéro avant chaque test ; un seul bean pour les trois ports. */
  static final class Fakes implements OrderRepository, ProductCatalog, IdGenerator {
    private FakeProductCatalog catalog = new FakeProductCatalog();
    private InMemoryOrderRepository repository = new InMemoryOrderRepository();
    private final AtomicLong ids = new AtomicLong();

    void reset() {
      catalog = new FakeProductCatalog();
      repository = new InMemoryOrderRepository();
      catalog.put(new CatalogProduct(P1, "Clavier", Money.of("10.00", "EUR"), 10));
      catalog.put(new CatalogProduct(P2, "Souris", Money.of("2.50", "EUR"), 10));
      catalog.put(new CatalogProduct(USD_PRODUCT, "Tapis", Money.of("5.00", "USD"), 10));
      catalog.put(new CatalogProduct(LAST_ONE, "Écran", Money.of("99.00", "EUR"), 1));
    }

    @Override
    public void save(Order order) {
      repository.save(order);
    }

    @Override
    public Optional<Order> findByIdAndCustomer(OrderId id, CustomerId customerId) {
      return repository.findByIdAndCustomer(id, customerId);
    }

    @Override
    public List<OrderSummary> findPageByCustomer(
        CustomerId customerId, @Nullable OrderCursor after, int limit) {
      return repository.findPageByCustomer(customerId, after, limit);
    }

    @Override
    public Result<CatalogProduct> find(ProductId id) {
      return catalog.find(id);
    }

    @Override
    public UUID next() {
      return new UUID(0x7000L, ids.incrementAndGet());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class Config {
    private static final Clock CLOCK =
        Clock.fixed(Instant.parse("2026-10-22T10:15:30Z"), ZoneOffset.UTC);

    @Bean
    Fakes fakes() {
      return new Fakes();
    }

    @Bean
    PlaceOrder placeOrder(Fakes fakes) {
      return new PlaceOrder(fakes, fakes, CLOCK, fakes);
    }

    @Bean
    GetOrder getOrder(Fakes fakes) {
      return new GetOrder(fakes);
    }

    @Bean
    ListOrders listOrders(Fakes fakes) {
      return new ListOrders(fakes);
    }
  }

  @Autowired MockMvcTester mvc;
  @Autowired Fakes fakes;

  @BeforeEach
  void resetFakes() {
    fakes.reset();
  }

  // ---- aides

  private static String item(ProductId product, int quantity) {
    return "{\"productId\":\"" + product.value() + "\",\"quantity\":" + quantity + "}";
  }

  private static String items(String... items) {
    return "{\"items\":[" + String.join(",", items) + "]}";
  }

  private MvcTestResult post(@Nullable String customer, String body) {
    var request = mvc.post().uri("/v1/orders").contentType(MediaType.APPLICATION_JSON);
    if (customer != null) {
      request = request.header("X-Customer-Id", customer);
    }
    return request.content(body).exchange();
  }

  private MvcTestResult get(String customer, String uri) {
    return mvc.get().uri(uri).header("X-Customer-Id", customer).exchange();
  }

  private static JsonNode json(MvcTestResult result) {
    try {
      return JSON.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private void assertApiError(MvcTestResult result, int status, String code) {
    assertThat(result).hasStatus(status);
    JsonNode body = json(result);
    assertThat(body.get("statusCode").asInt()).isEqualTo(status);
    assertThat(body.get("code").asString()).isEqualTo(code);
    assertThat(body.get("message").asString()).isNotBlank();
    assertThat(body.get("details").isArray()).isTrue();
    assertThat(body.get("traceId").asString()).isNotBlank();
    assertThat(body.size()).isEqualTo(5);
    String text = body.toString();
    assertThat(text).doesNotContain("Exception").doesNotContain("com.shop").doesNotContain("\tat ");
  }

  private void assertRejectedAndNothingSaved(MvcTestResult result) {
    assertApiError(result, 400, "VALIDATION_ERROR");
    assertThat(fakes.repository.count()).isZero();
  }

  private String placeOne() {
    MvcTestResult created = post(CUSTOMER, items(item(P1, 2)));
    assertThat(created).hasStatus(201);
    return json(created).get("id").asString();
  }

  // ---- T10

  @Test
  void t10ValidPostGives201WithLocationAndOrderBody() {
    MvcTestResult result = post(CUSTOMER, items(item(P1, 2), item(P2, 4)));

    assertThat(result).hasStatus(201);
    JsonNode body = json(result);
    String id = body.get("id").asString();
    assertThat(result.getResponse().getHeader("Location")).isEqualTo("/v1/orders/" + id);
    assertThat(body.get("customerId").asString()).isEqualTo(CUSTOMER);
    assertThat(body.get("createdAt").asString()).isEqualTo("2026-10-22T10:15:30Z");
    assertThat(body.get("items")).hasSize(2);
    JsonNode first = body.get("items").get(0);
    assertThat(first.get("productId").asString()).isEqualTo(P1.value().toString());
    assertThat(first.get("productName").asString()).isEqualTo("Clavier");
    assertThat(first.get("unitPrice").get("amount").asString()).isEqualTo("10.00");
    assertThat(first.get("unitPrice").get("currency").asString()).isEqualTo("EUR");
    assertThat(first.get("quantity").asInt()).isEqualTo(2);
    assertThat(first.get("lineTotal").get("amount").asString()).isEqualTo("20.00");
    assertThat(body.get("items").get(1).get("lineTotal").get("amount").asString())
        .isEqualTo("10.00");
    assertThat(body.get("total").get("amount").asString()).isEqualTo("30.00");
    assertThat(body.get("total").get("currency").asString()).isEqualTo("EUR");
    assertThat(fakes.repository.count()).isEqualTo(1);
  }

  // ---- T11

  @Test
  void t11NoLineIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, items()));
  }

  @Test
  void t11MissingItemsIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, "{}"));
  }

  @Test
  void t11FiftyOneLinesAreRejected() {
    List<String> lines = new ArrayList<>();
    for (int i = 0; i < 51; i++) {
      lines.add(item(new ProductId(new UUID(9, i)), 1));
    }
    assertRejectedAndNothingSaved(post(CUSTOMER, items(lines.toArray(String[]::new))));
  }

  @Test
  void t11QuantityZeroIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, items(item(P1, 0))));
  }

  @Test
  void t11QuantityOneHundredAndOneIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, items(item(P1, 101))));
  }

  @Test
  void t11MissingQuantityIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, items("{\"productId\":\"" + P1.value() + "\"}")));
  }

  @Test
  void t11DuplicateProductIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, items(item(P1, 1), item(P1, 2))));
  }

  @Test
  void t11MissingCustomerHeaderIsRejected() {
    assertRejectedAndNothingSaved(post(null, items(item(P1, 1))));
  }

  @Test
  void t11CustomerHeaderThatIsNotAUuidIsRejected() {
    assertRejectedAndNothingSaved(post("pas-un-uuid", items(item(P1, 1))));
  }

  @Test
  void t11UnknownJsonFieldIsRejected() {
    assertRejectedAndNothingSaved(
        post(CUSTOMER, "{\"items\":[" + item(P1, 1) + "],\"price\":\"1.00\"}"));
  }

  @Test
  void t11UnreadableBodyIsRejected() {
    assertRejectedAndNothingSaved(post(CUSTOMER, "{"));
  }

  // ---- T12

  @Test
  void t12UnknownProductGives400() {
    ProductId unknown = new ProductId(new UUID(9, 9));
    assertApiError(post(CUSTOMER, items(item(unknown, 1))), 400, "PRODUCT_NOT_FOUND");
    assertThat(fakes.repository.count()).isZero();
  }

  @Test
  void t12InsufficientStockGives409() {
    assertApiError(post(CUSTOMER, items(item(LAST_ONE, 2))), 409, "INSUFFICIENT_STOCK");
    assertThat(fakes.repository.count()).isZero();
  }

  @Test
  void t12MixedCurrenciesGives409() {
    assertApiError(
        post(CUSTOMER, items(item(P1, 1), item(USD_PRODUCT, 1))), 409, "MIXED_CURRENCIES");
    assertThat(fakes.repository.count()).isZero();
  }

  @Test
  void t12CatalogUnavailableGives503() {
    fakes.catalog.makeUnavailable();
    assertApiError(post(CUSTOMER, items(item(P1, 1))), 503, "CATALOG_UNAVAILABLE");
    assertThat(fakes.repository.count()).isZero();
  }

  // ---- T16

  @Test
  void t16ReadOwnOrderGives200() {
    String id = placeOne();

    MvcTestResult result = get(CUSTOMER, "/v1/orders/" + id);

    assertThat(result).hasStatus(200);
    JsonNode body = json(result);
    assertThat(body.get("id").asString()).isEqualTo(id);
    assertThat(body.get("total").get("amount").asString()).isEqualTo("20.00");
  }

  @Test
  void t16UnknownOrderAndOtherCustomersOrderGiveIdentical404() {
    String id = placeOne();

    MvcTestResult unknown = get(CUSTOMER, "/v1/orders/" + new UUID(5, 5));
    MvcTestResult foreign = get(OTHER_CUSTOMER, "/v1/orders/" + id);

    assertApiError(unknown, 404, "ORDER_NOT_FOUND");
    assertApiError(foreign, 404, "ORDER_NOT_FOUND");
    assertThat(withoutTraceId(foreign)).isEqualTo(withoutTraceId(unknown));
  }

  private static JsonNode withoutTraceId(MvcTestResult result) {
    ObjectNode copy = (ObjectNode) json(result).deepCopy();
    copy.remove("traceId");
    return copy;
  }

  @Test
  void t16OrderIdThatIsNotAUuidGives400() {
    assertApiError(get(CUSTOMER, "/v1/orders/pas-un-uuid"), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16ReadWithoutCustomerHeaderGives400() {
    assertApiError(
        mvc.get().uri("/v1/orders/" + new UUID(5, 5)).exchange(), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16LimitZeroGives400ValidationError() {
    assertApiError(get(CUSTOMER, "/v1/orders?limit=0"), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16LimitOneHundredAndOneGives400ValidationError() {
    assertApiError(get(CUSTOMER, "/v1/orders?limit=101"), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16LimitThatIsNotANumberGives400ValidationError() {
    assertApiError(get(CUSTOMER, "/v1/orders?limit=abc"), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16UnreadableCursorGives400ValidationError() {
    assertApiError(get(CUSTOMER, "/v1/orders?cursor=!!pas-un-curseur!!"), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16WellEncodedButMeaninglessCursorGives400ValidationError() {
    String cursor =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString("n'importe quoi".getBytes(StandardCharsets.UTF_8));
    assertApiError(get(CUSTOMER, "/v1/orders?cursor=" + cursor), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16ListWithoutHeaderGives400() {
    assertApiError(mvc.get().uri("/v1/orders").exchange(), 400, "VALIDATION_ERROR");
  }

  @Test
  void t16ListBoundsAreInclusive() {
    assertThat(get(CUSTOMER, "/v1/orders?limit=1")).hasStatus(200);
    assertThat(get(CUSTOMER, "/v1/orders?limit=100")).hasStatus(200);
  }

  @Test
  void listPagesWithOpaqueCursorAndOmitsNextCursorOnLastPage() {
    placeOne();
    placeOne();
    placeOne();

    JsonNode first = json(get(CUSTOMER, "/v1/orders?limit=2"));
    assertThat(first.get("items")).hasSize(2);
    assertThat(first.get("items").get(0).get("itemCount").asInt()).isEqualTo(1);
    assertThat(first.get("items").get(0).get("total").get("amount").asString()).isEqualTo("20.00");
    String cursor = first.get("nextCursor").asString();
    assertThat(cursor).matches("[A-Za-z0-9_-]+");

    JsonNode second = json(get(CUSTOMER, "/v1/orders?limit=2&cursor=" + cursor));
    assertThat(second.get("items")).hasSize(1);
    assertThat(second.has("nextCursor")).isFalse();
    assertThat(second.get("items").get(0).get("id").asString())
        .isNotEqualTo(first.get("items").get(0).get("id").asString())
        .isNotEqualTo(first.get("items").get(1).get("id").asString());
  }

  @Test
  void listDefaultsToTwentyAndIgnoresOtherCustomers() {
    placeOne();

    JsonNode other = json(get(OTHER_CUSTOMER, "/v1/orders"));

    assertThat(other.get("items")).isEmpty();
    assertThat(other.has("nextCursor")).isFalse();
  }
}
