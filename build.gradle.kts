import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
    alias(libs.plugins.errorprone)
}

version = "0.2.0"

springBoot {
    buildInfo { excludes.set(setOf("time")) }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(libs.versions.java.get().toInt()) }
}

dependencyLocking { lockAllConfigurations() }

dependencyManagement {
    imports { mavenBom(libs.spring.modulith.bom.get().toString()) }
}

dependencies {
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.flyway.core)
    implementation(libs.spring.modulith.starter.core)
    implementation(libs.springdoc.openapi.webmvc.ui)
    implementation(libs.jspecify)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.flyway.database.postgresql)
    errorprone(libs.errorprone.core)
    errorprone(libs.nullaway)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc.test)
    testImplementation(libs.spring.modulith.starter.test)
    testImplementation(libs.spring.modulith.docs)
    testImplementation(libs.archunit.junit5)
    testImplementation(libs.spring.tx)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Verrou 3 : une violation de nullabilité est une erreur de compilation
tasks.withType<JavaCompile>().configureEach {
    options.errorprone {
        check("NullAway", CheckSeverity.ERROR)
        option("NullAway:AnnotatedPackages", "com.shop.orders")
        option("NullAway:JSpecifyMode", "true")
        disableWarningsInGeneratedCode = true
    }
}

// Verrou 4 : formatage vérifié en CI (spotlessCheck), appliqué en local (spotlessApply)
spotless {
    java {
        googleJavaFormat()
        removeUnusedImports()
    }
}

// Suites séparées : `test` (unitaires + verrous d'architecture) et `integrationTest`
testing {
    suites {
        val test by getting(JvmTestSuite::class) { useJUnitJupiter() }
        register<JvmTestSuite>("integrationTest") {
            useJUnitJupiter()
            dependencies {
                implementation(project())
                implementation(sourceSets["test"].output)
                implementation(libs.testcontainers.postgresql)
                implementation(libs.testcontainers.junit.jupiter)
                implementation(libs.spring.boot.testcontainers)
                implementation(libs.spring.boot.starter.data.jpa.test)
            }
            targets { all { testTask.configure { shouldRunAfter(test) } } }
        }
    }
}

// La suite integrationTest hérite des dépendances de test et voit les classes et ressources de src/test
configurations.named("integrationTestImplementation") {
    extendsFrom(configurations.testImplementation.get())
}
configurations.named("integrationTestRuntimeOnly") {
    extendsFrom(configurations.testRuntimeOnly.get())
}

tasks.named("check") { dependsOn(tasks.named("integrationTest")) }

tasks.withType<Test>().configureEach {
    systemProperty("project.version", project.version.toString())
}

// Export du contrat OpenAPI sans serveur ni base : lance OpenApiExportTest puis affiche build/openapi.json
tasks.register<Test>("openapiExport") {
    description = "Exporte le contrat OpenAPI vers build/openapi.json et l'affiche"
    group = "documentation"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform()
    filter { includeTestsMatching("com.shop.orders.OpenApiExportTest") }
    outputs.upToDateWhen { false }
    doLast { print(layout.buildDirectory.file("openapi.json").get().asFile.readText()) }
}
