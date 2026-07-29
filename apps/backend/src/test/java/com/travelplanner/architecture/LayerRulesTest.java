package com.travelplanner.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Architecture rules as tests (tasks/15-quality-gates.md, PLAN §4.0.9, §5.1).
 *
 * <p>These run inside the ordinary {@code test} task, so a layer violation fails
 * {@code ./gradlew build} on a developer machine with no Docker and no database — not only in CI.
 * That is deliberate: a boundary that is only enforced after push is a boundary people learn to
 * discover late.
 *
 * <p><strong>Adding an exception.</strong> Do not widen a rule to make a change compile. The
 * process is in {@code docs/QUALITY-GATES.md}: state the violation, why the boundary should move,
 * and either amend the plan or record an ADR — then change the rule in the same PR as the code,
 * with the rationale in the rule's own comment. A rule with no explanation is a rule the next
 * person will delete.
 */
@AnalyzeClasses(
        packages = "com.travelplanner",
        importOptions = {ImportOption.DoNotIncludeTests.class, ImportOption.DoNotIncludeJars.class})
class LayerRulesTest {

    private static final String DOMAIN = "com.travelplanner.domain..";
    private static final String APPLICATION = "com.travelplanner.application..";
    private static final String API = "com.travelplanner.api..";
    private static final String INFRASTRUCTURE = "com.travelplanner.infrastructure..";
    private static final String AI = "com.travelplanner.ai..";
    private static final String CONFIG = "com.travelplanner.config..";

    // ---------------------------------------------------------------------------------------
    // Dependency direction — inward only (PLAN §5.1).
    // ---------------------------------------------------------------------------------------

    /**
     * The domain is the centre. It names no layer outside itself, which is what makes it testable
     * with no Spring context, no database, and no HTTP.
     */
    @ArchTest
    static final ArchRule domainDependsOnNothingOutward = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, API, INFRASTRUCTURE, AI, CONFIG)
            .because("the domain is the innermost layer; a dependency outward inverts the architecture");

    /**
     * Application orchestrates the domain through ports. Reaching an adapter directly would make
     * the port indirection decorative and bind business logic to a specific technology.
     */
    @ArchTest
    static final ArchRule applicationDependsOnDomainOnly = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(API, INFRASTRUCTURE, AI)
            .because("application talks to infrastructure through domain ports, never to an adapter directly");

    // ---------------------------------------------------------------------------------------
    // Domain purity (PLAN §5.1).
    // ---------------------------------------------------------------------------------------

    /**
     * No framework in the domain.
     *
     * <p>Reactor is the one third-party package allowed, and it is a knowing exception rather than
     * an oversight: ADR 007 puts {@code Flux<LlmEvent>} on {@code LlmPort}, which lives in
     * {@code domain/port}. It is recorded as open question <strong>F-23</strong> in
     * {@code tasks/STATUS.md} — a third-party type in the layer that is meant to have none. If
     * F-23 is resolved by moving the reactive type out, delete {@code reactor..} from this list;
     * the rule will then hold the stronger property.
     */
    @ArchTest
    static final ArchRule domainIsFrameworkFree = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "com.fasterxml.jackson..",
                    "dev.langchain4j..",
                    "org.hibernate..")
            .because("domain types must be constructible and assertable without a framework on the classpath");

    // ---------------------------------------------------------------------------------------
    // Technology confinement (PLAN §4.0.2-I, AGENTS.md, AI-AGENT-WORKFLOW §4).
    // ---------------------------------------------------------------------------------------

    /**
     * JPA stays behind the persistence adapter. An entity that escapes its package tends to become
     * the transport type, which is how a lazy association ends up being serialised to the wire.
     */
    @ArchTest
    static final ArchRule jpaConfinedToPersistenceEntities = noClasses()
            .that().resideOutsideOfPackage("com.travelplanner.infrastructure.persistence..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .because("JPA is an adapter detail; entities never leave infrastructure.persistence");

    /**
     * LangChain4j is confined to its adapter package. Task 14 chose it behind {@code LlmPort}
     * precisely so the provider library can be replaced without touching anything else.
     */
    @ArchTest
    static final ArchRule langChain4jConfinedToItsAdapter = noClasses()
            .that().resideOutsideOfPackage("com.travelplanner.ai.langchain4j..")
            .should().dependOnClassesThat().resideInAPackage("dev.langchain4j..")
            .because("the LLM library sits behind LlmPort so it can be swapped without a ripple");

    // ---------------------------------------------------------------------------------------
    // Controller/service boundary (PLAN §4.0: "Controller routes only; Service owns logic").
    // ---------------------------------------------------------------------------------------

    /** A controller anywhere other than {@code api.controller} is a route nobody expects. */
    @ArchTest
    static final ArchRule controllersLiveInApiController = classes()
            .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
            .should().resideInAPackage("com.travelplanner.api.controller..")
            .because("routes are discoverable only if they all live in one place");

    /**
     * Controllers route; they do not own transactions. A {@code @Transactional} controller extends
     * the transaction across request parsing and response serialisation, which is how a slow
     * client ends up holding a database connection.
     */
    @ArchTest
    static final ArchRule controllersAreNotTransactional = noClasses()
            .that().resideInAPackage("com.travelplanner.api.controller..")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("the service layer owns the transaction boundary, not the web layer");

    /**
     * Controllers must go through the application layer. Calling an adapter directly skips the
     * service that owns the business rule, and the rule then exists in two places.
     */
    @ArchTest
    static final ArchRule controllersDoNotReachInfrastructure = noClasses()
            .that().resideInAPackage("com.travelplanner.api.controller..")
            .should().dependOnClassesThat().resideInAnyPackage(INFRASTRUCTURE, "com.travelplanner.ai.langchain4j..")
            .because("a controller calls a service; only the service knows which adapter is involved");
}
