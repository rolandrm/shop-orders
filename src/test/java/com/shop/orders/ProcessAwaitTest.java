package com.shop.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessAwaitTest {

  @TempDir Path dir;

  private Process start(Path log, String script) throws IOException {
    ProcessBuilder builder = new ProcessBuilder("/bin/sh", "-c", script);
    builder.redirectErrorStream(true);
    builder.redirectOutput(log.toFile());
    return builder.start();
  }

  @Test
  void t4TimeoutKillsProcessAndReportsLastHundredLines() throws Exception {
    Path log = dir.resolve("t4.log");
    Process process =
        start(log, "i=1; while [ $i -le 150 ]; do echo ligne-$i; i=$((i+1)); done; exec sleep 60");

    Throwable thrown =
        catchThrowable(() -> ProcessAwait.awaitExit(process, Duration.ofSeconds(2), log));

    assertThat(thrown).isInstanceOf(AssertionError.class).hasMessageContaining("ligne-150");
    String message = String.valueOf(thrown.getMessage());
    List<String> lines = message.lines().toList();
    for (int i = 51; i <= 150; i++) {
      assertThat(lines).contains("ligne-" + i);
    }
    assertThat(lines).doesNotContain("ligne-1");
    assertThat(process.isAlive()).isFalse();
  }

  @Test
  void t5NonZeroExitReportsCodeAndLog() throws Exception {
    Path log = dir.resolve("t5.log");
    Process process = start(log, "echo ligne-ecrite; exit 3");

    assertThatThrownBy(() -> ProcessAwait.awaitExit(process, Duration.ofSeconds(30), log))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("code de sortie 3")
        .hasMessageContaining("ligne-ecrite");
  }

  @Test
  void t6ZeroExitBeforeTimeoutReturnsZero() throws Exception {
    Path log = dir.resolve("t6.log");
    Process process = start(log, "exit 0");

    assertThat(ProcessAwait.awaitExit(process, Duration.ofSeconds(30), log)).isZero();
  }
}
