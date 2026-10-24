package com.shop.orders.platform.config;

import java.util.List;

/**
 * Liste unique des variables d'environnement obligatoires. Une ligne par variable ; ajouter une
 * variable = ajouter une ligne.
 */
public final class RequiredVariables {

  /**
   * Une variable obligatoire.
   *
   * @param name nom de la variable
   * @param requiredInMigrate vrai si elle reste obligatoire avec le profil {@code migrate}
   */
  public record Entry(String name, boolean requiredInMigrate) {}

  /** Profil du conteneur de migration. */
  public static final String MIGRATE_PROFILE = "migrate";

  private static final List<Entry> ALL =
      List.of(
          new Entry("APP_SERVICE_NAME", false),
          new Entry("SHOP_API_BASE_URL", false),
          new Entry("SPRING_DATASOURCE_URL", true),
          new Entry("SPRING_DATASOURCE_USERNAME", true),
          new Entry("SPRING_DATASOURCE_PASSWORD", true));

  private RequiredVariables() {}

  /** Variables exigées, selon que le profil {@code migrate} est actif ou non. */
  public static List<Entry> forProfile(boolean migrate) {
    return ALL.stream().filter(entry -> !migrate || entry.requiredInMigrate()).toList();
  }
}
