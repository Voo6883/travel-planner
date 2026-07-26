package com.travelplanner.api.error;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Keeps the two halves of the error catalog in lockstep: the OpenAPI registry the frontend
 * generates from, and the Java enum the backend maps statuses with.
 *
 * <p>Without this test the failure mode is silent and late — the backend returns a code that is
 * not in the contract, the generated union does not contain it, and the user sees a raw
 * {@code snake_case} identifier where a translated message belongs.
 */
class ErrorCatalogTest {

    private static final Path ERRORS_YAML =
            Path.of("src/main/java/com/travelplanner/api/openapi/errors.yaml");

    @Test
    void registeredCodesMatchTheJavaCatalogExactly() {
        assertThat(specCodes())
                .as("errors.yaml ErrorCode enum vs ApiErrorCode — see the procedure at the top "
                        + "of errors.yaml")
                .containsExactlyInAnyOrderElementsOf(javaCodes());
    }

    @Test
    void codesAreSnakeCase() {
        assertThat(javaCodes()).allMatch(code -> code.matches("^[a-z][a-z0-9_]*$"));
    }

    @Test
    void everyCodeResolvesToAnI18nKeyUnderTheCommonErrorsNamespace() {
        // PLAN §6.1: the frontend maps `code` → `common.errors.<code>`; it never shows `message`.
        assertThat(ApiErrorCode.VERSION_CONFLICT.i18nKey()).isEqualTo("errors.version_conflict");
        assertThat(ApiErrorCode.values())
                .allMatch(code -> code.i18nKey().equals("errors." + code.code()));
    }

    @Test
    void statusesMatchTheContract() {
        assertThat(ApiErrorCode.VALIDATION_FAILED.status().value()).isEqualTo(400);
        assertThat(ApiErrorCode.UNAUTHORIZED.status().value()).isEqualTo(401);
        assertThat(ApiErrorCode.FORBIDDEN.status().value()).isEqualTo(403);
        assertThat(ApiErrorCode.NOT_FOUND.status().value()).isEqualTo(404);
        assertThat(ApiErrorCode.VERSION_CONFLICT.status().value()).isEqualTo(409);
        assertThat(ApiErrorCode.INTERNAL_ERROR.status().value()).isEqualTo(500);
    }

    @Test
    void unknownCodesDoNotResolve() {
        assertThat(ApiErrorCode.fromCode("definitely_not_registered")).isEmpty();
        assertThat(ApiErrorCode.fromCode("version_conflict")).contains(ApiErrorCode.VERSION_CONFLICT);
    }

    private static List<String> javaCodes() {
        return java.util.Arrays.stream(ApiErrorCode.values()).map(ApiErrorCode::code).toList();
    }

    private static List<String> specCodes() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        OpenAPI catalog = new OpenAPIV3Parser()
                .readLocation(ERRORS_YAML.toString(), null, options)
                .getOpenAPI();
        assertThat(catalog).as("parsed %s", ERRORS_YAML.toAbsolutePath()).isNotNull();
        return catalog.getComponents().getSchemas().get("ErrorCode").getEnum().stream()
                .map(String::valueOf)
                .toList();
    }
}
