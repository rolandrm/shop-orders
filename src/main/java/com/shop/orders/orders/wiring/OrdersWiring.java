package com.shop.orders.orders.wiring;

import com.shop.orders.orders.application.GetOrder;
import com.shop.orders.orders.application.ListOrders;
import com.shop.orders.orders.application.PlaceOrder;
import com.shop.orders.orders.domain.OrderRepository;
import com.shop.orders.orders.domain.ProductCatalog;
import com.shop.orders.platform.config.ShopApiProperties;
import com.shop.orders.sharedkernel.port.IdGenerator;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Assemblage du module {@code orders}. */
@Configuration(proxyBeanMethods = false)
class OrdersWiring {

  /** Client de shop-api : URL de base, délai de connexion et délai de lecture des propriétés. */
  @Bean
  RestClient shopApiRestClient(ShopApiProperties properties) {
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  @Bean
  PlaceOrder placeOrder(
      ProductCatalog catalog, OrderRepository repository, Clock clock, IdGenerator idGenerator) {
    return new PlaceOrder(catalog, repository, clock, idGenerator);
  }

  @Bean
  GetOrder getOrder(OrderRepository repository) {
    return new GetOrder(repository);
  }

  @Bean
  ListOrders listOrders(OrderRepository repository) {
    return new ListOrders(repository);
  }
}
