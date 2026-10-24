package com.shop.orders.platform.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import errorfixture.ErrorFixtureController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = ErrorFixtureController.class)
@Import({ErrorFixtureController.class, GlobalExceptionHandler.class, RequestIdFilter.class})
class GlobalExceptionHandlerTest {

  @Autowired MockMvc mvc;

  private void expectError(String path, int status, String code) throws Exception {
    mvc.perform(get(path))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.statusCode").value(status))
        .andExpect(jsonPath("$.code").value(code))
        .andExpect(jsonPath("$.message").isNotEmpty())
        .andExpect(jsonPath("$.details").isArray())
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void notFoundMapsTo404() throws Exception {
    expectError("/fixture/not-found", 404, "ORDER_NOT_FOUND");
  }

  @Test
  void validationMapsTo400() throws Exception {
    expectError("/fixture/validation", 400, "ORDER_INVALID");
  }

  @Test
  void conflictMapsTo409() throws Exception {
    expectError("/fixture/conflict", 409, "ORDER_CONFLICT");
  }

  @Test
  void unexpectedExceptionIsHiddenBehind500() throws Exception {
    MvcResult result =
        mvc.perform(get("/fixture/boom"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andReturn();
    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("secret interne")
        .doesNotContain("IllegalStateException")
        .doesNotContainPattern("at [A-Za-z_.]+\\.[A-Za-z_]+");
  }

  @Test
  void invalidBodyGives400ValidationError() throws Exception {
    mvc.perform(
            post("/fixture/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.details[0].field").value("name"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void unavailableMapsTo503() throws Exception {
    expectError("/fixture/unavailable", 503, "CATALOG_UNAVAILABLE");
  }

  @Test
  void missingHeaderGives400ValidationError() throws Exception {
    expectError("/fixture/header", 400, "VALIDATION_ERROR");
  }

  @Test
  void unreadableUuidGives400ValidationError() throws Exception {
    expectError("/fixture/uuid?id=pas-un-uuid", 400, "VALIDATION_ERROR");
  }

  @Test
  void outOfBoundsParameterGives400ValidationError() throws Exception {
    expectError("/fixture/bounded?limit=0", 400, "VALIDATION_ERROR");
    expectError("/fixture/bounded?limit=101", 400, "VALIDATION_ERROR");
  }

  @Test
  void unknownJsonFieldGives400ValidationError() throws Exception {
    mvc.perform(
            post("/fixture/body")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"a\",\"inconnu\":1}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }

  @Test
  void unreadableBodyGives400WithoutInternalDetail() throws Exception {
    MvcResult result =
        mvc.perform(post("/fixture/body").contentType(MediaType.APPLICATION_JSON).content("{"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andReturn();
    assertThat(result.getResponse().getContentAsString())
        .doesNotContain("Exception")
        .doesNotContain("com.");
  }

  @Test
  void unknownRouteGives404InCommonFormat() throws Exception {
    mvc.perform(get("/does-not-exist"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.statusCode").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());
  }
}
