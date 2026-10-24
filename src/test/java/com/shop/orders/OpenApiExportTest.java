package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.shop.orders.orders.domain.OrderRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

  @MockitoBean OrderRepository orderRepository; // pas de base dans la suite test

  @Autowired MockMvc mockMvc;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMapping;

  @Test
  void exportsContractAndCoversEveryEndpoint() throws Exception {
    String body =
        mockMvc
            .perform(get("/docs/openapi.json"))
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);

    Path target = Path.of("build", "openapi.json");
    Files.createDirectories(target.getParent());
    Files.writeString(target, body, StandardCharsets.UTF_8);

    JsonNode json = JsonMapper.builder().build().readTree(body);
    assertThat(json.get("openapi").asString()).startsWith("3.");
    assertThat(json.get("info").get("title").asString()).isEqualTo("shop-orders");
    assertThat(json.get("info").get("description").asString()).isNotBlank();
    assertThat(json.get("info").get("version").asString())
        .isEqualTo(System.getProperty("project.version"));

    JsonNode get = json.get("paths").get("/docs/openapi.json").get("get");
    assertThat(get.get("summary").asString()).isNotBlank();
    assertThat(get.get("responses").get("200").get("description").asString()).isNotBlank();
    assertThat(get.get("responses").get("500").get("description").asString()).isNotBlank();

    JsonNode paths = json.get("paths");
    assertOperation(paths, "/v1/orders", "post", List.of("201", "400", "409", "500", "503"), 1);
    assertOperation(paths, "/v1/orders", "get", List.of("200", "400", "500"), 3);
    assertOperation(paths, "/v1/orders/{orderId}", "get", List.of("200", "400", "404", "500"), 2);
    assertThat(json.get("components").get("schemas").has("ApiError")).isTrue();
    for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
      String beanPackage = entry.getValue().getBeanType().getPackageName();
      if (beanPackage.startsWith("org.springdoc")
          || beanPackage.startsWith("org.springframework.boot")) {
        continue;
      }
      RequestMappingInfo info = entry.getKey();
      Set<String> patterns =
          info.getPathPatternsCondition() == null
              ? Set.of()
              : info.getPathPatternsCondition().getPatternValues();
      for (String pattern : patterns) {
        assertThat(paths.has(pattern)).as(pattern).isTrue();
      }
    }
  }

  /** Présence seulement (pas le texte) : résumé, paramètres et réponses décrits. */
  private static void assertOperation(
      JsonNode paths, String path, String method, List<String> statuses, int parameterCount) {
    String label = method + " " + path;
    assertThat(paths.has(path)).as(label).isTrue();
    JsonNode operation = paths.get(path).get(method);
    assertThat(operation).as(label).isNotNull();
    assertThat(operation.get("summary").asString()).as(label + " summary").isNotBlank();

    JsonNode parameters = operation.get("parameters");
    assertThat(parameters).as(label + " parameters").hasSize(parameterCount);
    for (JsonNode parameter : parameters) {
      String name = parameter.get("name").asString();
      assertThat(parameter.get("description").asString()).as(label + " " + name).isNotBlank();
    }
    assertThat(
            StreamSupport.stream(parameters.spliterator(), false)
                .map(p -> p.get("name").asString()))
        .as(label + " X-Customer-Id")
        .contains("X-Customer-Id");

    JsonNode responses = operation.get("responses");
    for (String status : statuses) {
      assertThat(responses.has(status)).as(label + " " + status).isTrue();
      assertThat(responses.get(status).get("description").asString())
          .as(label + " " + status + " description")
          .isNotBlank();
    }
    for (String status : statuses.stream().filter(s -> !s.startsWith("2")).toList()) {
      assertThat(
              responses
                  .get(status)
                  .get("content")
                  .get("application/json")
                  .get("schema")
                  .get("$ref")
                  .asString())
          .as(label + " " + status + " ApiError")
          .isEqualTo("#/components/schemas/ApiError");
    }
  }
}
