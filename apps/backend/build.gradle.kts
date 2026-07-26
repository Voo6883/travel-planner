// Travel Planner backend — Spring Boot API.
// Contract: plans/superpower/PLAN.md §4.0 (Java 21, Gradle Kotlin DSL, wrapper committed).
//
// Lint/coverage/ArchUnit gates are deliberately absent here — tasks/15-quality-gates.md owns
// those thresholds. Adding them now would pre-empt a later task's scope.

// The Flyway Gradle plugin loads database dialects from its OWN classloader, not from a project
// configuration, so `flyway-database-postgresql` has to be a buildscript dependency. Without it
// `flywayValidate` fails with "No Flyway database plugin found to handle jdbc:postgresql://...".
buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.flywaydb:flyway-database-postgresql:11.7.2")
    }
}

plugins {
    java
    id("org.springframework.boot") version "3.5.3"
    // Supplies `flywayValidate` / `flywayMigrate` against a REAL database, which is the command
    // tasks/07-database-domain-foundation.md names under Validation. It is deliberately not part
    // of `check`: it needs a reachable Postgres, and `./gradlew build` must stay runnable with no
    // Docker and no database (established in tasks 02 and 04).
    id("org.flywaydb.flyway") version "11.7.2"
}

group = "com.travelplanner"
version = "0.0.1-SNAPSHOT"
description = "Travel Planner backend — Spring Boot API"

java {
    toolchain {
        // Java 21 is a locked decision (PLAN §1). The toolchain makes it a build-time
        // failure rather than a runtime surprise on a machine with a different default JDK.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// -----------------------------------------------------------------------------------------
// Test suite separation (PLAN §4.0.2-K).
//
// `test`            — unit and slice tests. No Docker, no database. Runs inside `check`/`build`.
// `integrationTest` — Testcontainers Postgres: migrations, mapping, transactions, locking.
//                     NOT wired into `check`, because Testcontainers fails hard rather than
//                     degrading when no Docker daemon is present. CI runs it as an explicit step.
//
// The source directory is `src/test/integration/`, which is the path PLAN §4.0.2-K names, rather
// than Gradle's conventional `src/integrationTest/`.
// -----------------------------------------------------------------------------------------
val integrationTestSourceSet: SourceSet by lazy { sourceSets["integrationTest"] }

sourceSets {
    create("integrationTest") {
        java.setSrcDirs(listOf("src/test/integration/java"))
        resources.setSrcDirs(listOf("src/test/integration/resources"))
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

val integrationTestImplementation: Configuration by configurations.getting {
    extendsFrom(configurations.testImplementation.get())
}
val integrationTestRuntimeOnly: Configuration by configurations.getting {
    extendsFrom(configurations.testRuntimeOnly.get())
}
val integrationTestAnnotationProcessor: Configuration by configurations.getting {
    extendsFrom(configurations.testAnnotationProcessor.get())
}

// Classpath for the Flyway Gradle plugin's own tasks. A dedicated configuration rather than the
// application's runtimeClasspath: `flywayValidate` needs exactly the driver and the Postgres
// dialect plugin, and resolving the whole Spring runtime to run one DDL check is both slow and a
// way for an unrelated dependency conflict to break a schema command.
val flywayMigration: Configuration by configurations.creating

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JDBC stays declared explicitly even though starter-data-jpa pulls it in:
    // DatabaseReadinessContributor (task 04) uses javax.sql.DataSource directly and must not
    // silently depend on JPA remaining on the classpath.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")

    // Flyway owns every schema change (PLAN §4.0.2-H). flyway-database-postgresql is a separate
    // artifact from Flyway 10 onward; without it Flyway cannot recognise a Postgres JDBC URL.
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")

    // @Retryable for deadlock recovery (PLAN §4.0.2-E2). spring-retry needs AOP proxies.
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // Explicit JPA entity <-> domain mapping (PLAN §4.0.2-I). Compile-time generated, so a
    // mapping that cannot be satisfied is a build failure rather than a runtime null.
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Validates api/openapi/openapi.yaml in the backend build (task 06). Test-only on purpose:
    // the contract is hand-authored and design-first, so nothing generates or serves it at
    // runtime and the production image stays free of the parser and its transitive tree.
    testImplementation("io.swagger.parser.v3:swagger-parser-v3:2.1.22")

    // Testcontainers is scoped to the integrationTest source set only, so nothing on the `test`
    // classpath can accidentally start reaching for a Docker daemon.
    //
    // 1.21.4 is a floor, not a preference: Docker Engine 29 refuses API versions below 1.40, and
    // the docker-java client shaded into 1.21.3 negotiates 1.32. On a current Docker Desktop the
    // older release cannot reach the daemon at all.
    integrationTestImplementation(platform("org.testcontainers:testcontainers-bom:1.21.4"))
    integrationTestImplementation("org.testcontainers:junit-jupiter")
    integrationTestImplementation("org.testcontainers:postgresql")
    integrationTestImplementation("org.springframework.boot:spring-boot-testcontainers")
    integrationTestRuntimeOnly("org.postgresql:postgresql")

    // Flyway 10+ ships each database dialect as its own artifact; without the Postgres one the
    // plugin reports "No Flyway database plugin found to handle jdbc:postgresql://...".
    flywayMigration(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    flywayMigration("org.flywaydb:flyway-database-postgresql")
    flywayMigration("org.postgresql:postgresql")
}

// Configured from the environment so the same command works on a host-run Postgres and in CI.
// No credential default is a secret: these are the committed local development values (.env.example).
flyway {
    url = System.getenv("SPRING_DATASOURCE_URL")
        ?: "jdbc:postgresql://localhost:5432/travel_planner"
    user = System.getenv("POSTGRES_USER") ?: "travel_planner"
    password = System.getenv("POSTGRES_PASSWORD") ?: "travel_planner"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    configurations = arrayOf(flywayMigration.name)
    cleanDisabled = true
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // -parameters keeps constructor/method parameter names for Spring binding and
    // for OpenAPI generation in task 06.
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Testcontainers Postgres suite: migrations, mapping, transactions, locking. Needs Docker."
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
}
