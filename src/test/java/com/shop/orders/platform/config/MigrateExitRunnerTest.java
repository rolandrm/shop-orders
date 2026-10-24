package com.shop.orders.platform.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class MigrateExitRunnerTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(Config.class);

  @Configuration(proxyBeanMethods = false)
  @Import(MigrateExitRunner.class)
  static class Config {}

  @Test
  void existsWithMigrateProfile() {
    runner
        .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("migrate"))
        .run(ctx -> assertThat(ctx).hasSingleBean(MigrateExitRunner.class));
  }

  @Test
  void absentWithoutMigrateProfile() {
    runner.run(ctx -> assertThat(ctx).doesNotHaveBean(MigrateExitRunner.class));
  }

  @Test
  void absentWhenExitDisabled() {
    runner
        .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("migrate"))
        .withPropertyValues("app.migrate.exit-on-completion=false")
        .run(ctx -> assertThat(ctx).doesNotHaveBean(MigrateExitRunner.class));
  }
}
