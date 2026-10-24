package com.shop.orders.platform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Profil {@code migrate} : une fois la migration appliquée et le schéma validé, ferme le contexte
 * et termine le processus avec le code 0 (un thread non-démon retiendrait sinon la JVM). Désactivé
 * par {@code app.migrate.exit-on-completion=false} (tests exécutés dans la JVM du worker).
 */
@Component
@Profile("migrate")
@ConditionalOnProperty(
    name = "app.migrate.exit-on-completion",
    havingValue = "true",
    matchIfMissing = true)
public class MigrateExitRunner implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MigrateExitRunner.class);

  private final ApplicationContext context;

  public MigrateExitRunner(ApplicationContext context) {
    this.context = context;
  }

  @Override
  public void run(ApplicationArguments args) {
    LOG.info("Migration terminée : arrêt du processus");
    System.exit(SpringApplication.exit(context));
  }
}
