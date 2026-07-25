# ADR 001: Gradle as backend build tool

## Status

Accepted

## Context

The plan originally listed Maven vs Gradle as an open question (§11). A build tool is
required for prerequisite checks, CI, and Docker multi-stage builds before any backend
code can ship.

## Decision

Use **Gradle 8.x** with **Kotlin DSL** (`build.gradle.kts`). Commit the **Gradle wrapper**
(`gradlew`, `gradle/wrapper/`) — developers and CI never need a global Gradle install.

## Rationale

| Factor | Gradle | Maven |
|---|---|---|
| Incremental builds | Faster for large Spring projects | Adequate |
| Java 21 toolchain | Native toolchain block | Supported via plugin |
| Spring Boot 3.x | First-class support | First-class support |
| Monorepo future | Composite builds possible | Less ergonomic |
| New project defaults | Common for greenfield Spring Boot | Enterprise legacy default |

## Consequences

- Prerequisite script checks `./gradlew --version` (or wrapper presence).
- CI runs `./gradlew build`, `./gradlew test`, Spotless/Checkstyle via Gradle plugins.
- Docker backend stage: `./gradlew bootJar -x test`.
- Maven documentation/examples in external tutorials must be translated to Gradle.

## Alternatives considered

- **Maven** — rejected for slower iteration on a greenfield monorepo; can revisit only via new ADR if team mandate requires it.
