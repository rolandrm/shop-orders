package com.shop.orders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DocsUiDisabledTest {

  @Autowired MockMvc mockMvc;

  @Test
  void docsUiIsNotFoundWithoutApiDocsUi() throws Exception {
    mockMvc.perform(get("/docs")).andExpect(status().isNotFound());
  }
}
