package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
class OpenApiExportTest {

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
}
