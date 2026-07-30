// Travel Planner backend — Spring Boot API.
// Contract: plans/superpower/PLAN.md §4.0 (Java 21, Gradle Kotlin DSL, wrapper committed).
//
// Lint/coverage/ArchUnit gates land here with tasks/15-quality-gates.md. Thresholds, exclusions,
// and the architecture-exception process are documented in docs/QUALITY-GATES.md — change them
// there and here together, never only here.

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
    // Task 15 gates. Both are Gradle built-ins, so neither adds a plugin-resolution dependency
    // that could take CI down when a plugin portal is unavailable.
    checkstyle
    jacoco
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

    // Task 08 — the authentication boundary (ADR 002, ADR 006, ADR 009). The starter supplies the
    // filter chain, the CSRF token repository, and BCrypt; nimbus-jose-jwt signs and verifies the
    // self-issued access token. Nimbus rather than a JWT convenience wrapper because it is already
    // managed by the Spring Boot BOM and is what Spring Security itself uses, so the project gains
    // no new transitive tree.
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Version pinned explicitly: the Spring Boot BOM does not manage this coordinate (it reaches
    // Spring Security only transitively, through the OAuth2 JOSE module this project does not use).
    implementation("com.nimbusds:nimbus-jose-jwt:10.0.2")

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

    // Task 14 — the AI provider platform (ADR 007).
    //
    // Reactor Core, not WebFlux. `LlmPort` returns `Flux<LlmEvent>` (ADR 007 supersedes PLAN
    // §5.1's `Flux<String>`), and a Flux is all that is needed: the application stays a servlet
    // stack, and task 20 bridges the Flux onto SSE. Pulling in `spring-boot-starter-webflux`
    // would add a second, competing web server autoconfiguration for one type.
    // The version is managed by the Spring Boot BOM's reactor-bom import.
    implementation("io.projectreactor:reactor-core")

    // LangChain4j is confined to `ai/langchain4j/` (AGENTS.md, AI-AGENT-WORKFLOW §4). It is a
    // normal `implementation` dependency rather than `runtimeOnly` because the adapters compile
    // against it; the import rule is enforced by review today and by ArchUnit in task 15.
    //
    // Neither artifact requires a key to be on the classpath: the provider beans are
    // @ConditionalOnProperty and the default provider is the deterministic stub, so `./gradlew
    // build` and CI never need ANTHROPIC_API_KEY or OPENAI_API_KEY.
    implementation(platform("dev.langchain4j:langchain4j-bom:1.18.0"))
    implementation("dev.langchain4j:langchain4j-anthropic")
    implementation("dev.langchain4j:langchain4j-open-ai")

    // Task 15 — Actuator and Prometheus (PLAN §4.0.9). Exposure is locked down per profile in
    // application.yml / application-prod.yml, not here: the dependency only makes the endpoints
    // available, the configuration decides which of them the network can reach.
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    runtimeOnly("io.micrometer:micrometer-registry-prometheus")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Task 15 — layer rules as tests (PLAN §4.0.9). ArchUnit runs in the ordinary `test` task so a
    // violation fails `./gradlew build` on a developer machine, with no Docker and no database,
    // rather than only in CI.
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Hot reload during host dev (`npm run dev:backend`). Restarts on classpath changes when
    // paired with `bootRun --continuous`; excluded from production images automatically.
    //
    // The BOM is repeated here because `developmentOnly` does not extend `implementation`, so it
    // inherits none of that configuration's dependency management. Without this line the
    // coordinate below has no version, and `bootJar` — which resolves `developmentOnly` in order
    // to EXCLUDE it from the fat jar — fails with "Could not find spring-boot-devtools:".
    // `bootRun` never resolves it, which is why the gap survived commit 038221d: hot reload
    // worked while `./gradlew build` was broken.
    developmentOnly(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    developmentOnly("org.springframework.boot:spring-boot-devtools")

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
        // A failure without its message is a line number and a class name — enough to know something
        // broke, not enough to know what. The default (SHORT) prints `java.lang.AssertionError at
        // Foo.java:79` and drops the message, which is where the whole diagnosis lives: the seed
        // validator, for one, reports every bad row in that message. FULL puts it on the console so a
        // failure is actionable without opening the HTML report.
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        // Frames from JUnit, Gradle and the JDK's reflection plumbing, which are never the cause.
        showStackTraces = false
    }
}

tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "Testcontainers Postgres suite: migrations, mapping, transactions, locking. Needs Docker."
    testClassesDirs = integrationTestSourceSet.output.classesDirs
    classpath = integrationTestSourceSet.runtimeClasspath
    shouldRunAfter(tasks.named("test"))
}

// -----------------------------------------------------------------------------------------
// Task 15 — Checkstyle (PLAN §4.0.9, §13).
//
// Scoped to `main` and the two test source sets, and deliberately narrow: line length, naming,
// and import hygiene. Task 15 explicitly forbids enforcing subjective method/file size heuristics,
// so no such module appears in the ruleset.
//
// `maxWarnings = 0` with every rule at severity=error means a violation fails the build rather
// than printing into a report nobody opens.
// -----------------------------------------------------------------------------------------
checkstyle {
    toolVersion = "10.21.0"
    configFile = rootProject.file("config/checkstyle/checkstyle.xml")
    maxWarnings = 0
    maxErrors = 0
}

// Generated MapStruct implementations are written into build/generated and compiled as part of
// `main`. They are machine output — holding them to a hand-written style rule fails the build for
// formatting the project does not control.
tasks.withType<Checkstyle>().configureEach {
    exclude("**/generated/**", "**/*MapperImpl.java")
    reports {
        xml.required = true
        html.required = true
    }
}

// -----------------------------------------------------------------------------------------
// Task 15 — JaCoCo (PLAN §4.0.9).
//
// The threshold applies to `domain/` and `application/` ONLY. That is the whole point: those two
// layers hold the business rules, they are pure enough to unit-test without infrastructure, and a
// project-wide percentage would let thin, well-covered adapters subsidise an untested domain.
//
// Everything else is excluded because covering it proves nothing about correctness:
//   config/         — Spring wiring; exercised by context load, asserts no behaviour
//   api/            — controllers; covered by slice tests that JaCoCo attributes to the servlet
//   infrastructure/ — adapters; the real proof is the Testcontainers suite, which runs separately
//                     in `integrationTest` and so contributes no data to this report
//   *MapperImpl     — MapStruct output
// -----------------------------------------------------------------------------------------
private val coveredPackages = listOf("com/travelplanner/domain/**", "com/travelplanner/application/**")
private val coverageExclusions = listOf("**/*MapperImpl.class", "**/config/**")

jacoco {
    toolVersion = "0.8.12"
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
    classDirectories.setFrom(
        files(
            sourceSets.main.get().output.classesDirs.map { dir ->
                fileTree(dir) {
                    include(coveredPackages)
                    exclude(coverageExclusions)
                }
            },
        ),
    )
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    classDirectories.setFrom(tasks.jacocoTestReport.get().classDirectories)
    violationRules {
        rule {
            // Measured when task 15 landed: LINE 86.3% (1179/1366), BRANCH 75.4% (344/456).
            // Thresholds sit just below those figures, so a change that meaningfully reduces
            // coverage fails rather than eroding it silently. This is a ratchet — raise it as
            // coverage improves, never lower it to make a change pass (task 15: "Do not weaken
            // gates merely to make generated code pass"). docs/QUALITY-GATES.md records the
            // measurement and the process for changing these numbers.
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.85".toBigDecimal()
            }
            // Branch gets a wider margin than line on purpose. A single added conditional moves
            // branch coverage several points while barely touching lines, so a tight branch gate
            // fails honest work and teaches people to lower it — which is how a gate dies.
            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}

// The gates run as part of `check`, which `build` depends on — so `./gradlew build` is the single
// command that proves the foundation locally and in CI (task 15 Definition of Done).
tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
