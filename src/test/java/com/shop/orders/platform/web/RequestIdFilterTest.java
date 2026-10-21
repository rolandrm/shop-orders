package com.shop.orders.platform.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import errorfixture.ErrorFixtureController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = ErrorFixtureController.class)
@Import({ErrorFixtureController.class, GlobalExceptionHandler.class, RequestIdFilter.class})
class RequestIdFilterTest {

  @Autowired MockMvc mvc;

  @Test
  void incomingRequestIdIsEchoedAndUsedAsTraceId() throws Exception {
    mvc.perform(get("/fixture/not-found").header("X-Request-Id", "abc"))
        .andExpect(header().string("X-Request-Id", "abc"))
        .andExpect(jsonPath("$.traceId").value("abc"));
  }

  @Test
  void missingRequestIdIsGenerated() throws Exception {
    mvc.perform(get("/fixture/not-found"))
        .andExpect(header().exists("X-Request-Id"))
        .andExpect(header().string("X-Request-Id", org.hamcrest.Matchers.not("")));
  }
}
