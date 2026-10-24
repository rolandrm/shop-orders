package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.orders.orders.domain.OrderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTest {

  @MockitoBean OrderRepository orderRepository; // pas de base dans la suite test

  @LocalServerPort int port;

  @Autowired Environment environment;

  private ResponseEntity<String> get(String path) {
    return RestClient.builder()
        .baseUrl("http://localhost:" + port)
        .defaultStatusHandler(status -> true, (request, response) -> {})
        .build()
        .get()
        .uri(path)
        .retrieve()
        .toEntity(String.class);
  }

  @Test
  void healthEndpointsAreUpWithoutComponents() {
    for (String path :
        new String[] {
          "/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness"
        }) {
      ResponseEntity<String> response = get(path);
      assertThat(response.getStatusCode().value()).as(path).isEqualTo(200);
      assertThat(response.getBody()).as(path).contains("\"status\":\"UP\"");
      assertThat(response.getBody()).as(path).doesNotContain("components");
    }
  }

  @Test
  void envEndpointIsNotExposed() {
    assertThat(get("/actuator/env").getStatusCode().value()).isEqualTo(404);
  }

  @Test
  void environmentCarriesImposedSettings() {
    assertThat(environment.getProperty("spring.threads.virtual.enabled")).isEqualTo("true");
    assertThat(environment.getProperty("server.error.include-stacktrace")).isEqualTo("never");
  }
}
