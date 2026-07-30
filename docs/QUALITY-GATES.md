# Quality Gates

> Deliverable of [Task 15](../tasks/15-quality-gates.md) — the **Handoff** section requires exact
> commands, thresholds, exclusions, and the process for justified architecture exceptions.
> Owner of the numbers: this file. Change a threshold here and in the build file together.

## 1. Run every gate

Two commands. Both are what CI runs, so a green machine means a green pipeline.

```bash
cd apps/backend && ./gradlew build
```

```bash
cd apps/frontend && npm run lint && npm run format:check && npm run typecheck && npm run test:coverage && npm run build
```

The backend needs neither Docker nor a database. The Testcontainers suite is deliberately
excluded from `build` and runs separately:

```bash
cd apps/backend && ./gradlew integrationTest
```

## 2. What each gate is, and where it runs

| Gate | Tool | Command | Fails the build via |
|---|---|---|---|
| Line length, naming, imports | Checkstyle 10.21.0 | `./gradlew checkstyleMain checkstyleTest checkstyleIntegrationTest` | `check` |
| Layer boundaries | ArchUnit 1.3.0 | `./gradlew test --tests '*LayerRulesTest*'` | `test`, inside `check` |
| Backend coverage | JaCoCo 0.8.12 | `./gradlew jacocoTestCoverageVerification` | `check` |
| Schema against a real Postgres | Testcontainers | `./gradlew integrationTest` | explicit CI step |
| TS strictness + import boundaries | ESLint 9 | `npm run lint` | CI step |
| Formatting | Prettier 3.4.2 | `npm run format:check` | CI step |
| Frontend coverage | Vitest v8 | `npm run test:coverage` | CI step |
| Generated client drift | openapi-typescript | `npm run codegen` + `git status --porcelain` | CI step |

## 3. Thresholds — measured, not aspirational

Every number below was **measured** when task 15 landed and then set at or just under the
measurement. They are a **ratchet**: raise them as coverage improves, never lower them to make a
change pass. Task 15 states this directly — *"Do not weaken gates merely to make generated code
pass."*

### Backend — JaCoCo

Scope is `com/travelplanner/domain/**` and `com/travelplanner/application/**` **only**.

| Counter | Measured | Threshold |
|---|---|---|
| LINE | 86.3% (1179/1366) | **85%** |
| BRANCH | 75.4% (344/456) | **70%** |

Branch gets the wider margin on purpose: one added conditional moves branch coverage several
points while barely touching lines, and a gate that fails honest work is a gate people lower.

**Excluded, and why** — covering these proves nothing about correctness:

| Excluded | Reason |
|---|---|
| `config/**` | Spring wiring. Exercised by context load; asserts no behaviour. |
| `api/**` | Controllers; slice tests attribute coverage to the servlet, not the route. |
| `infrastructure/**` | Adapters. The real proof is `integrationTest`, which runs separately and contributes no data to this report. |
| `**/*MapperImpl` | MapStruct output. |

A project-wide percentage was rejected: it lets thin, well-covered adapters subsidise an untested
domain, which is the opposite of what the number should mean.

### Frontend — Vitest

Scope is `src/components`, `src/features`, `src/hooks`, `src/lib`. Measured across 226 tests in
15 files.

| Counter | Measured | Threshold |
|---|---|---|
| Statements | 35.85% (1294/3609) | **35%** |
| Lines | 35.85% (1294/3609) | **35%** |
| Functions | 59.16% (113/191) | **55%** |
| Branches | 77.53% (252/325) | **70%** |

Statement coverage is low and the threshold is honest about it rather than flattering: most of
`src/components` and `src/features` is presentational markup with no test. Branch coverage is high
because the code that *has* branches — API error mapping, auth state, form validation — is well
covered. A single blended number would hide both facts.

`src/app/**` is excluded: App Router files are route declarations and server-component shells, so
importing one to raise a percentage proves the module parses, not that anything works. `**/index.ts`
is excluded because barrels re-export and hold no behaviour — counting them rewards adding exports.

## 4. Architecture rules

`apps/backend/src/test/java/com/travelplanner/architecture/LayerRulesTest.java` — 8 rules:

| Rule | Enforces |
|---|---|
| `domainDependsOnNothingOutward` | The domain names no layer outside itself |
| `applicationDependsOnDomainOnly` | Application reaches infrastructure only through domain ports |
| `domainIsFrameworkFree` | No Spring, JPA, Jackson, Hibernate or LangChain4j in the domain |
| `jpaConfinedToPersistenceEntities` | `jakarta.persistence` only in `infrastructure.persistence` |
| `langChain4jConfinedToItsAdapter` | `dev.langchain4j` only in `ai.langchain4j` |
| `controllersLiveInApiController` | `@RestController` only in `api.controller` |
| `controllersAreNotTransactional` | The service layer owns the transaction boundary |
| `controllersDoNotReachInfrastructure` | A controller calls a service, never an adapter |

Frontend boundaries live in `apps/frontend/eslint.config.mjs` as **disjoint zones**. See §6 —
overlapping zones silently disable rules.

### Known exceptions

Two, both deliberate and both recorded as open questions rather than hidden:

| Exception | Where | Tracked as |
|---|---|---|
| ~~`reactor..` permitted in the domain~~ | `domainIsFrameworkFree` | **F-23 closed** 2026-07-30. The streaming turn is on `application/ai/LlmStreamPort`; `reactor..` is now forbidden in the domain and the rule passes. **Do not add an entry back** — a domain type that appears to need a framework is in the wrong package |
| `components/layout/**` may import `@/features/auth` | ESLint zone | **F-25** — the shared app shell renders identity |

`ConstantName` is suppressed for the architecture test package only
(`apps/backend/config/checkstyle/suppressions.xml`): ArchUnit uses the **field name as the test
name**, so `SCREAMING_SNAKE_CASE` would make every failure message harder to read at the moment
someone most needs to read it.

## 5. Adding a justified exception

Do **not** widen a rule to make a change compile. The process:

1. **State the violation** — paste the actual ArchUnit or ESLint output into the PR.
2. **Argue the boundary, not the change.** The question is never "is this code fine?" but "should
   the boundary be here?" If the answer is that the boundary is wrong, the rule moves. If the
   answer is that this one case is special, the rule stays and the code changes.
3. **Record it.** A moved boundary that contradicts the plan needs an ADR or a plan amendment. A
   tolerated violation needs an `F-nn` entry in [`tasks/STATUS.md`](../tasks/STATUS.md) so it is
   visible as debt rather than as settled design.
4. **Change the rule in the same PR as the code**, with the rationale in the rule's own comment. A
   suppression with no explanation is indistinguishable from one added to get a build green — which
   is exactly what the next person will assume, and delete.

Precedent: task 15 found `AdminUserDirectory` (application layer) taking `PageQuery` from
`api.dto.page`. The type had no web dependency of its own, so the **package** was the error. It
moved to `application.page` and the rule stayed strict. That is the default outcome.

## 6. Trap: ESLint `no-restricted-imports` does not merge

Flat-config objects do not merge rule *options*. When two objects both match a file, the later one
**replaces** the rule rather than adding to it.

An earlier draft of `eslint.config.mjs` expressed the four boundaries as four cascading blocks, the
last of which matched `src/**`. The result: three of the four rules silently did nothing, and
`npm run lint` passed on a file importing another feature.

The zones are therefore **disjoint**, and each restates every restriction that applies to it. After
changing them, prove the gate still bites — write files that should be rejected, run `npm run lint`,
confirm each is reported, then delete them:

```bash
# from apps/frontend
printf "import { useCurrentUser } from '@/features/auth';\nexport const p = useCurrentUser;\n" > src/lib/__probe.ts
printf "import { useCurrentUser } from '@/features/auth';\nexport const p = useCurrentUser;\n" > src/features/admin/__probe.ts
npm run lint   # expect 2 errors; if it passes, a zone is overlapping and the rule is dead
rm src/lib/__probe.ts src/features/admin/__probe.ts
```

A boundary rule that cannot be shown to fail is not a gate.

## 7. Actuator exposure

| Profile | Endpoints | Port | Health detail |
|---|---|---|---|
| default / `local` / `docker` | `health,info,metrics,prometheus` | 8080 (app) | `always` |
| `prod` | `health,prometheus` | **9090** | `never` |

Never `*`. `env` and `configprops` print resolved configuration including secret-shaped values, so
they are unexposed and return 404 in every profile. Verified:

```
/actuator/health 200   /actuator/env         404
/actuator/info   200   /actuator/configprops 404
/actuator/metrics 200  /actuator/beans       404
/actuator/prometheus 200  /actuator/heapdump 404
```

In production the separate management port is the control that matters: the public ingress fronts
8080, so operator endpoints are not routable from outside the network. `SecurityConfig`
`actuatorSecurityFilterChain` exists because the API chain is scoped to `/api/**` — without it,
`/actuator/**` would match no filter chain at all and be served with no security applied.
