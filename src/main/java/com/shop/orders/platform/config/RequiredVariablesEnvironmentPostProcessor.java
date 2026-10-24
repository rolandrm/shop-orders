package com.shop.orders.platform.config;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

/**
 * Refuse le démarrage, avant toute création de bean, si une variable obligatoire de {@link
 * RequiredVariables} est absente ou vide. Le message nomme les variables, jamais une valeur.
 */
public class RequiredVariablesEnvironmentPostProcessor
    implements EnvironmentPostProcessor, Ordered {

  /** Début fixe du message d'échec. */
  public static final String MESSAGE_PREFIX =
      "Variables d'environnement obligatoires absentes ou vides : ";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    boolean migrate = environment.acceptsProfiles(Profiles.of(RequiredVariables.MIGRATE_PROFILE));
    List<String> missing =
        RequiredVariables.forProfile(migrate).stream()
            .map(RequiredVariables.Entry::name)
            .filter(name -> isBlank(environment.getProperty(name)))
            .toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException(MESSAGE_PREFIX + String.join(", ", missing));
    }
  }

  private static boolean isBlank(@Nullable String value) {
    return value == null || value.isBlank();
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }
}
