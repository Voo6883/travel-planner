// Travel Planner backend — Spring Boot API.
// Contract: plans/superpower/PLAN.md §4.0 (Java 21, Gradle Kotlin DSL, wrapper committed).
//
// Lint/coverage/ArchUnit gates are deliberately absent here — tasks/15-quality-gates.md owns
// those thresholds. Adding them now would pre-empt a later task's scope.

plugins {
    java
    id("org.springframework.boot") version "3.5.3"
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

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // JDBC only — enough for /api/v1/ready to prove the database is reachable (task 04 DoD).
    // JPA entities and Flyway migrations belong to tasks/07-database-domain-foundation.md.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
