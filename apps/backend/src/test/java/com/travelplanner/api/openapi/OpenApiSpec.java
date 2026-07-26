package com.travelplanner.api.openapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads the hand-authored contract for the spec tests.
 *
 * <p>The spec lives under {@code src/main/java}, where PLAN §4.0.0.1 locks it, so it is not on the
 * test classpath — Gradle only compiles {@code .java} from that source set. Reading it from the
 * project directory is deliberate: the file the tests validate is byte-for-byte the file codegen
 * consumes, with no build step in between that could mask a difference.
 */
final class OpenApiSpec {

    static final Path DIRECTORY = Path.of("src/main/java/com/travelplanner/api/openapi");
    static final Path CONTRACT = DIRECTORY.resolve("openapi.yaml");
    static final Path ERROR_CATALOG = DIRECTORY.resolve("errors.yaml");

    private OpenApiSpec() {
    }

    /** Parses and fully resolves a spec document, failing the test on any validation message. */
    static OpenAPI load(Path specPath) {
        assertThat(Files.exists(specPath))
                .as("spec file %s — run the backend tests from apps/backend", specPath.toAbsolutePath())
                .isTrue();

        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setValidateExternalRefs(true);

        SwaggerParseResult result = new OpenAPIV3Parser().readLocation(specPath.toString(), null, options);

        assertThat(result.getMessages())
                .as("OpenAPI validation messages for %s", specPath)
                .isEmpty();
        assertThat(result.getOpenAPI()).as("parsed document for %s", specPath).isNotNull();
        return result.getOpenAPI();
    }
}
