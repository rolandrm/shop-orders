package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Profil {@code migrate} dans un processus séparé, configuration de production seule : le processus
 * doit se terminer de lui-même avec le code 0, puis le schéma doit exister.
 */
class MigrateProcessExitIT {

  static final Duration MIGRATE_TIMEOUT = Duration.ofSeconds(180);

  @Test
  void migrateProcessExitsWithZeroAndCreatesSchema() throws IOException, InterruptedException {
    String url = PostgresContainer.createEmptyDatabase();
    Path log = Paths.get("build", "migrate-process-exit.log");
    Files.createDirectories(log.getParent());

    String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    ProcessBuilder builder =
        new ProcessBuilder(
            List.of(
                java,
                "-cp",
                System.getProperty("java.class.path"),
                "com.shop.orders.Application",
                "--spring.profiles.active=migrate",
                "--spring.config.location=classpath:/application.yml"));
    Map<String, String> env = builder.environment();
    env.clear();
    env.put("SPRING_DATASOURCE_URL", url);
    env.put("SPRING_DATASOURCE_USERNAME", PostgresContainer.username());
    env.put("SPRING_DATASOURCE_PASSWORD", PostgresContainer.password());
    builder.redirectErrorStream(true);
    builder.redirectOutput(log.toFile());
    builder.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));

    Process process = builder.start();
    try {
      assertThat(MIGRATE_TIMEOUT).isEqualTo(Duration.ofSeconds(180));
      ProcessAwait.awaitExit(process, MIGRATE_TIMEOUT, log);
    } finally {
      process.destroyForcibly();
    }

    JdbcClient jdbc =
        JdbcClient.create(
            new DriverManagerDataSource(
                url, PostgresContainer.username(), PostgresContainer.password()));
    for (String table : List.of("orders_order", "orders_order_line")) {
      Integer count =
          jdbc.sql("SELECT count(*) FROM information_schema.tables WHERE table_name = :name")
              .param("name", table)
              .query(Integer.class)
              .single();
      assertThat(count).as(table).isEqualTo(1);
    }
  }
}
