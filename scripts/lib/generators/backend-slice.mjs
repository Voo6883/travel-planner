/**
 * `npm run generate:backend-slice -- <feature-slug>`
 *
 * PLAN §4.0.8's vertical slice, as files. A feature in this codebase is the same seven-shaped thing
 * every time — controller, service, command, view, port, adapter, test — and the parts that go wrong
 * are never the business logic. They are the mechanical ones:
 *
 *   - a package line that does not match the directory, so nothing compiles and the error names javac
 *   - a service reaching an adapter directly, which `applicationDependsOnDomainOnly` catches, but only
 *     after a build
 *   - a controller with `@Transactional` on it, which `controllersAreNotTransactional` catches likewise
 *   - a port declared and no adapter written, so the context fails to start with an unsatisfied bean
 *   - a write method with no `@TransactionalWrite`, which nothing catches at all
 *
 * The last one is the reason this exists. Everything else is caught by a gate; a missing transaction
 * boundary is a silent partial write, and the template has it already on.
 *
 * <b>Skeletons only.</b> Every file compiles and every file has a `TODO` where the thinking goes. The
 * generator does not invent a domain model, a schema, or an endpoint contract — it lays out the
 * boundaries so an agent spends its context on the decisions instead of on the layout.
 */

import {
  ChangeSet,
  printNextSteps,
  toCamelCase,
  toJavaPackageSegment,
  toPascalCase,
  validateSlug,
} from '../generate-support.mjs';

const BACKEND = 'apps/backend/src/main/java/com/travelplanner';
const BACKEND_TEST = 'apps/backend/src/test/java/com/travelplanner';

export const usage = 'generate:backend-slice -- <feature-slug>          e.g. research-job';

export function plan(args) {
  const { slug, error } = validateSlug(args[0]);
  if (error !== null) {
    return { error: `generate:backend-slice: ${error}` };
  }

  const names = {
    slug,
    Pascal: toPascalCase(slug),
    camel: toCamelCase(slug),
    pkg: toJavaPackageSegment(slug),
    human: slug.replace(/-/g, ' '),
  };

  const changes = new ChangeSet(`generate:backend-slice — ${names.Pascal} (${names.human})`);
  changes.create(`${BACKEND}/domain/port/${names.Pascal}RepositoryPort.java`, portFile(names));
  changes.create(`${BACKEND}/application/${names.pkg}/${names.Pascal}Service.java`, serviceFile(names));
  changes.create(`${BACKEND}/application/${names.pkg}/Create${names.Pascal}Command.java`, commandFile(names));
  changes.create(`${BACKEND}/application/${names.pkg}/${names.Pascal}View.java`, viewFile(names));
  changes.create(`${BACKEND}/api/controller/${names.Pascal}Controller.java`, controllerFile(names));
  changes.create(`${BACKEND}/infrastructure/persistence/${names.Pascal}RepositoryAdapter.java`, adapterFile(names));
  changes.create(`${BACKEND_TEST}/application/${names.pkg}/${names.Pascal}ServiceTest.java`, testFile(names));

  return {
    changes,
    onDone: () => printNextSteps([
      `Model the domain first. \`domain/model/${names.Pascal}\` does not exist yet, on purpose — a generator that guessed a record's components would guess wrong, and a wrong domain model is the one mistake the layers above cannot absorb.`,
      `Run \`npm run generate:migration -- create_${names.pkg}_table\` if this slice persists anything.`,
      `Register the endpoint in \`api/openapi/openapi.yaml\`, then \`npm run codegen\`. The contract is the source of the frontend's types; a hand-written client is forbidden.`,
      `Register any new error code with \`npm run generate:error-code\` — five files, and two of them fail silently.`,
      `Delete every TODO. A TODO that survives review is a decision nobody made.`,
      'Run `npm run verify:fast`.',
    ]),
  };
}

function portFile(names) {
  return `package com.travelplanner.domain.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for ${names.human} (tasks/NN-*.md).
 *
 * <p>TODO: one sentence on what this port is for, and the query somebody will want and must not have.
 * {@code RefreshTokenPort} is the reference. Nothing in {@code domain/} or {@code application/} may
 * name the adapter.
 */
public interface ${names.Pascal}RepositoryPort {

    // TODO: replace ${names.Pascal} with the domain record once domain/model/${names.Pascal}.java exists.
    // Object save(Object ${names.camel});

    // Optional<Object> findById(UUID id);

    /** Placeholder so the interface compiles before the domain model is written. Delete it. */
    Optional<UUID> findIdById(UUID id);
}
`;
}

function serviceFile(names) {
  return `package com.travelplanner.application.${names.pkg};

import com.travelplanner.application.support.TransactionalWrite;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.port.${names.Pascal}RepositoryPort;
import com.travelplanner.domain.valueobject.UserContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ${names.human} use cases (tasks/NN-*.md, UC-???).
 *
 * <p>TODO: the rules this service owns, not the methods it has.
 *
 * <p>Every method takes {@link UserContext} as a parameter rather than reading a security context, so
 * a call site cannot forget to scope by owner (PLAN §4.0.2-L).
 */
@Service
@RequiresDatabase
public class ${names.Pascal}Service {

    private final ${names.Pascal}RepositoryPort ${names.camel}s;

    public ${names.Pascal}Service(${names.Pascal}RepositoryPort ${names.camel}s) {
        this.${names.camel}s = ${names.camel}s;
    }

    /** TODO: the read path. */
    @Transactional(readOnly = true)
    public UUID find(UUID id, UserContext caller) {
        // TODO: scope by caller.userId(); return a ${names.Pascal}View, never a domain record — a
        // domain type on the wire makes every future field an accidental API change.
        return ${names.camel}s.findIdById(id).orElseThrow(() -> new UnsupportedOperationException("TODO"));
    }

    /**
     * TODO: the write path.
     *
     * <p>{@link TransactionalWrite} must stay — no gate catches its absence. No LLM, supplier or search
     * call inside: each holds a pooled connection across a network wait, and a retry repeats it.
     */
    @TransactionalWrite
    public UUID create(Create${names.Pascal}Command command, UserContext caller) {
        throw new UnsupportedOperationException("TODO: implement ${names.Pascal}Service.create");
    }
}
`;
}

function commandFile(names) {
  return `package com.travelplanner.application.${names.pkg};

import java.util.Objects;

/**
 * The input to {@link ${names.Pascal}Service#create}.
 *
 * <p>A command record rather than a parameter list: PLAN §4.0.4 caps a signature at three parameters,
 * and its own stated remedy is this. It also gives validation somewhere to live that every call site
 * passes through — a compact constructor cannot be forgotten the way a guard clause can.
 *
 * <p>TODO: add the fields, and validate them here. "Blank means absent" and "absent means invalid" are
 * different rules and both belong in this constructor rather than in the service.
 */
public record Create${names.Pascal}Command(String name) {

    public Create${names.Pascal}Command {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
    }
}
`;
}

function viewFile(names) {
  return `package com.travelplanner.application.${names.pkg};

import java.util.UUID;

/**
 * What the API layer is handed for a ${names.human}.
 *
 * <p>A view rather than the domain record. A domain type on the wire makes every field it gains later
 * an unannounced API change, and every field it should not expose a leak that no reviewer sees because
 * nothing in the diff mentions it.
 *
 * <p>TODO: add the fields the endpoint publishes — and only those.
 */
public record ${names.Pascal}View(UUID id, String name) {
}
`;
}

function controllerFile(names) {
  return `package com.travelplanner.api.controller;

import com.travelplanner.application.${names.pkg}.${names.Pascal}Service;
import com.travelplanner.config.RequiresDatabase;
import com.travelplanner.domain.valueobject.UserContext;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ${names.human} over HTTP.
 *
 * <p><strong>Routing only.</strong> No business rule, no transaction, no port. ArchUnit enforces the
 * last two ({@code controllersAreNotTransactional}, {@code controllersDoNotReachInfrastructure}) and
 * PLAN §4.0.1 states the first. A controller that grows an {@code if} has taken a decision away from
 * the layer that can be tested without HTTP.
 *
 * <p>TODO: register these paths in {@code api/openapi/openapi.yaml} and run {@code npm run codegen}.
 * The contract is the source of the frontend's types — a hand-written client is forbidden, and the
 * drift gate fails the build if the two disagree.
 */
@RestController
@RequestMapping("/api/v1")
@RequiresDatabase
public class ${names.Pascal}Controller {

    private final ${names.Pascal}Service ${names.camel}s;

    public ${names.Pascal}Controller(${names.Pascal}Service ${names.camel}s) {
        this.${names.camel}s = ${names.camel}s;
    }

    @GetMapping("/${names.slug}s/{id}")
    public UUID get(@PathVariable UUID id, @AuthenticationPrincipal UserContext caller) {
        // TODO: return a response DTO from api/dto/, not a bare id and not the application view.
        return ${names.camel}s.find(id, caller);
    }
}
`;
}

function adapterFile(names) {
  return `package com.travelplanner.infrastructure.persistence;

import com.travelplanner.domain.port.${names.Pascal}RepositoryPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * {@link ${names.Pascal}RepositoryPort} over JPA.
 *
 * <p>{@code @ConditionalOnProperty("spring.datasource.url")} — via {@code @RequiresDatabase} on the
 * services — is what keeps {@code ./gradlew test} free of Docker. A bare {@code @Component} here
 * would make every slice test need a datasource, which is the trap {@code docs/HANDOFF-REMAINING-WORK.md}
 * §4.5 records.
 *
 * <p>TODO: add the JPA entity, the Spring Data repository, and a MapStruct mapper. Entities never
 * leave this package — {@code jpaConfinedToPersistenceEntities} enforces it, because an entity that
 * escapes becomes the transport type and a lazy association ends up on the wire.
 */
@Component
@ConditionalOnProperty(name = "spring.datasource.url")
public class ${names.Pascal}RepositoryAdapter implements ${names.Pascal}RepositoryPort {

    @Override
    public Optional<UUID> findIdById(UUID id) {
        throw new UnsupportedOperationException("TODO: implement ${names.Pascal}RepositoryAdapter");
    }
}
`;
}

function testFile(names) {
  return `package com.travelplanner.application.${names.pkg};

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * ${names.human} rules, without a Spring context (PLAN §4.0.2-K).
 *
 * <p>Hand-written fakes rather than mocks, matching {@code AuthTestFakes}: these tests are about
 * behaviour across several calls, and a mock returning a canned value per call lets an implementation
 * that never persists anything pass. See {@code AuthTestFakes.FakeRefreshTokens} for the shape,
 * including why its {@code markRotated} re-reads instead of trusting the caller's snapshot.
 *
 * <p>TODO: write the tests before the service. Each one should name the failure it prevents rather
 * than the method it calls — {@code rejectsAnExpiredToken} tells a reader something;
 * {@code testCreate} does not.
 */
class ${names.Pascal}ServiceTest {

    @Test
    void hasTestsBeforeItHasAnImplementation() {
        assertThatThrownBy(() -> {
            throw new UnsupportedOperationException("TODO: delete this and write real tests");
        }).isInstanceOf(UnsupportedOperationException.class);
    }
}
`;
}
