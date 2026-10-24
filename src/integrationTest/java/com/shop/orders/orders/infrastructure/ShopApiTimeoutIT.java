package com.shop.orders.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.orders.fakes.FakeProductCatalog;
import com.shop.orders.orders.fakes.InMemoryOrderRepository;
import com.shop.orders.platform.config.ShopApiProperties;
import com.shop.orders.sharedkernel.Result;
import com.shop.orders.sharedkernel.port.IdGenerator;
import com.sun.net.httpserver.HttpServer;
import jakarta.validation.Validation;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

/**
 * Délais de shop-api prouvés avec la vraie fabrique de requêtes, déclarée par {@code OrdersWiring},
 * contre un serveur local du JDK : aucun appel réseau externe.
 */
class ShopApiTimeoutIT {

  private static final ProductId PRODUCT =
      new ProductId(UUID.fromString("018f0000-0000-7000-8000-000000000001"));

  private static final String BODY =
      """
      {"id": "018f0000-0000-7000-8000-000000000001", "name": "Clavier",
       "price": {"amount": "10.00", "currency": "EUR"}, "stock": 7}
      """;

  @Test
  void responseAfterFourSecondsIsUnavailableBeforeThreePointNineSeconds() throws Exception {
    HttpServer server =
        HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            Thread.sleep(4_000);
            byte[] bytes = BODY.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
              out.write(bytes);
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } catch (IOException e) {
            // le client a abandonné avant la réponse : attendu
          } finally {
            exchange.close();
          }
        });
    server.start();
    try {
      ShopApiProductCatalog catalog =
          catalogFor("http://127.0.0.1:" + server.getAddress().getPort());

      long start = System.nanoTime();
      Result<CatalogProduct> result = catalog.find(PRODUCT);
      Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

      assertUnavailable(result);
      assertThat(elapsed).isLessThan(Duration.ofMillis(3_900));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void closedPortIsUnavailable() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }
    ShopApiProductCatalog catalog = catalogFor("http://127.0.0.1:" + closedPort);

    assertUnavailable(catalog.find(PRODUCT));
  }

  private static ShopApiProductCatalog catalogFor(String baseUrl) throws ClassNotFoundException {
    // OrdersWiring est package-private dans orders.wiring : chargé par son nom pour prouver le vrai
    // câblage
    Class<?> wiring = Class.forName("com.shop.orders.orders.wiring.OrdersWiring");
    AtomicReference<RestClient> client = new AtomicReference<>();
    new ApplicationContextRunner()
        .withBean(
            ShopApiProperties.class,
            () -> new ShopApiProperties(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(3)))
        // dépendances des cas d'usage que OrdersWiring assemble aussi (sans rapport avec ce test)
        .withBean(ProductCatalog.class, FakeProductCatalog::new)
        .withBean(OrderRepository.class, InMemoryOrderRepository::new)
        .withBean(Clock.class, Clock::systemUTC)
        .withBean(IdGenerator.class, () -> UUID::randomUUID)
        .withUserConfiguration(wiring)
        .run(context -> client.set(context.getBean(RestClient.class)));
    RestClient restClient = client.get();
    assertThat(restClient).isNotNull();
    return new ShopApiProductCatalog(
        restClient, Validation.buildDefaultValidatorFactory().getValidator());
  }

  private static void assertUnavailable(Result<CatalogProduct> result) {
    assertThat(result).isInstanceOf(Result.Failure.class);
    assertThat(((Result.Failure<CatalogProduct>) result).error().code())
        .isEqualTo(OrderErrors.CATALOG_UNAVAILABLE);
  }
}
