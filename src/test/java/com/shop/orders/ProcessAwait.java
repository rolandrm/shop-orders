package com.shop.orders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Attente d'un processus de test avec diagnostic : sur délai dépassé ou code de sortie non nul,
 * l'échec porte les dernières lignes du journal du processus.
 */
public final class ProcessAwait {

  static final int TAIL_LINES = 100;

  private ProcessAwait() {}

  /**
   * Attend la fin du processus et rend son code de sortie (0).
   *
   * @throws AssertionError si le délai est dépassé (le processus est alors tué) ou si le code de
   *     sortie n'est pas 0
   */
  public static int awaitExit(Process process, Duration timeout, Path log)
      throws InterruptedException {
    boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      process.waitFor(10, TimeUnit.SECONDS);
      throw new AssertionError(
          "Le processus ne s'est pas terminé en "
              + timeout.toSeconds()
              + " s (tué). Dernières lignes du journal :\n"
              + tail(log));
    }
    int code = process.exitValue();
    if (code != 0) {
      throw new AssertionError(
          "Le processus s'est terminé avec le code de sortie "
              + code
              + ". Dernières lignes du journal :\n"
              + tail(log));
    }
    return code;
  }

  private static String tail(Path log) {
    List<String> lines;
    try {
      lines = new String(Files.readAllBytes(log), StandardCharsets.UTF_8).lines().toList();
    } catch (IOException e) {
      return "(journal illisible : " + e.getClass().getSimpleName() + ")";
    }
    return String.join("\n", lines.subList(Math.max(0, lines.size() - TAIL_LINES), lines.size()));
  }
}
