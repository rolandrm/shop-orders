package com.shop.orders.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.shop.orders.orders.contract.ProductCatalogContract;
import com.shop.orders.orders.domain.CatalogProduct;
import com.shop.orders.orders.domain.OrderErrors;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.orders.domain.ProductId;
import com.shop.orders.sharedkernel.DomainError;
import com.shop.orders.sharedkernel.Result;
import jakarta.validation.Validation;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Même suite de contrat que {@code FakeProductCatalogContractTest}, contre l'adaptateur shop-api
 * dont le serveur est simulé par {@link MockRestServiceServer} (aucun appel réseau).
 */
class ShopApiProductCatalogContractIT extends ProductCatalogContract {

  private static final String BASE_URL = "http://shop-api.test.invalid";

  private MockRestServiceServer server;
  private ShopApiProductCatalog catalog;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    server = MockRestServiceServer.bindTo(builder).build();
    catalog =
        new ShopApiProductCatalog(
            builder.build(), Validation.buildDefaultValidatorFactory().getValidator());
  }

  @Override
  protected ProductCatalog catalog() {
    return catalog;
  }

  @Override
  protected void givenProduct(CatalogProduct product) {
    assertThat(product).as("la fixture product-200.json décrit KNOWN").isEqualTo(KNOWN);
    expectGet(product.id()).andRespond(withSuccess(fixture("product-200.json"), json()));
  }

  @Override
  protected void givenNoProduct(ProductId id) {
    expectGet(id)
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND).contentType(json()).body(fixture("product-404.json")));
  }

  @Override
  protected void givenCatalogFailing() {
    respondWith(HttpStatus.INTERNAL_SERVER_ERROR, "product-500.json");
  }

  @Test
  void unknownFieldsInA200ResponseAreIgnored() {
    expectGet(KNOWN.id()).andRespond(withSuccess(fixture("product-200-extra-field.json"), json()));

    Result<CatalogProduct> result = catalog.find(KNOWN.id());

    assertThat(result).isEqualTo(new Result.Success<>(KNOWN));
  }

  @Test
  void badRequestIsReportedAsUnavailable() {
    respondWith(HttpStatus.BAD_REQUEST, "product-400.json");

    assertUnavailable();
  }

  @Test
  void unreadableBodyIsReportedAsUnavailable() {
    expectGet(KNOWN.id()).andRespond(withSuccess(fixture("product-unreadable.json"), json()));

    assertUnavailable();
  }

  @Test
  void emptyBodyIsReportedAsUnavailable() {
    expectGet(KNOWN.id()).andRespond(withSuccess("", json()));

    assertUnavailable();
  }

  @Test
  void unexpectedSuccessStatusIsReportedAsUnavailable() {
    respondWith(HttpStatus.NO_CONTENT, "product-200.json");

    assertUnavailable();
  }

  @Test
  void bodyOutsideTheContractIsReportedAsUnavailable() {
    expectGet(KNOWN.id())
        .andRespond(
            withSuccess(
                """
                {"id": "018f0000-0000-7000-8000-000000000001", "name": "Clavier",
                 "price": {"amount": "10.00", "currency": "XXXX"}, "stock": 7}
                """,
                json()));

    assertUnavailable();
  }

  @Test
  void nameOf200CharactersIsKeptWhole() {
    String name = "a".repeat(200);
    expectGet(KNOWN.id()).andRespond(withSuccess(bodyWithName(name), json()));

    Result<CatalogProduct> result = catalog.find(KNOWN.id());

    assertThat(result).isInstanceOf(Result.Success.class);
    assertThat(((Result.Success<CatalogProduct>) result).value().name()).isEqualTo(name);
  }

  @Test
  void nameOf1CharacterIsKept() {
    expectGet(KNOWN.id()).andRespond(withSuccess(bodyWithName("a"), json()));

    Result<CatalogProduct> result = catalog.find(KNOWN.id());

    assertThat(result).isInstanceOf(Result.Success.class);
    assertThat(((Result.Success<CatalogProduct>) result).value().name()).isEqualTo("a");
  }

  @Test
  void nameOf201CharactersIsReportedAsUnavailable() {
    expectGet(KNOWN.id()).andRespond(withSuccess(bodyWithName("a".repeat(201)), json()));

    assertUnavailable();
  }

  @Test
  void emptyNameIsReportedAsUnavailable() {
    expectGet(KNOWN.id()).andRespond(withSuccess(bodyWithName(""), json()));

    assertUnavailable();
  }

  @Test
  void blankNameIsReportedAsUnavailable() {
    expectGet(KNOWN.id()).andRespond(withSuccess(bodyWithName("   "), json()));

    assertUnavailable();
  }

  @Test
  void callsTheDocumentedPathOnce() {
    givenProduct(KNOWN);

    catalog.find(KNOWN.id());

    server.verify();
  }

  private static String bodyWithName(String name) {
    return "{\"id\": \"018f0000-0000-7000-8000-000000000001\", \"name\": \""
        + name
        + "\", \"price\": {\"amount\": \"10.00\", \"currency\": \"EUR\"}, \"stock\": 7}";
  }

  private void respondWith(HttpStatus status, String fixtureName) {
    expectGet(KNOWN.id())
        .andRespond(withStatus(status).contentType(json()).body(fixture(fixtureName)));
  }

  private org.springframework.test.web.client.ResponseActions expectGet(ProductId id) {
    return server
        .expect(requestTo(BASE_URL + "/v1/products/" + id.value()))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Accept", "application/json"));
  }

  private void assertUnavailable() {
    Result<CatalogProduct> result = catalog.find(KNOWN.id());

    assertThat(result).isInstanceOf(Result.Failure.class);
    DomainError error = ((Result.Failure<CatalogProduct>) result).error();
    assertThat(error.code()).isEqualTo(OrderErrors.CATALOG_UNAVAILABLE);
  }

  private static MediaType json() {
    return MediaType.APPLICATION_JSON;
  }

  private static String fixture(String name) {
    try (InputStream in =
        ShopApiProductCatalogContractIT.class.getResourceAsStream("/shop-api/" + name)) {
      if (in == null) {
        throw new IllegalStateException("Fixture absente : " + name);
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Fixture illisible : " + name, e);
    }
  }
}
