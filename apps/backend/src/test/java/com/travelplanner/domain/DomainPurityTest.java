package com.travelplanner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Asserts the {@code domain} package imports nothing from a framework or a vendor
 * (PLAN §4.0.2-F, task 07 Definition of Done).
 *
 * <p>A source scan, not reflection: an annotation with {@code SOURCE} or {@code CLASS} retention is
 * invisible at runtime, so a class-level check would pass on a domain type that had picked up
 * {@code @Entity}. This is a narrow, task-scoped guard — the general architecture ruleset (ArchUnit
 * across every layer) belongs to {@code tasks/15-quality-gates.md} and is not duplicated here.
 */
class DomainPurityTest {

    private static final Path DOMAIN = Path.of("src/main/java/com/travelplanner/domain");

    private static final List<String> FORBIDDEN_IMPORT_PREFIXES = List.of(
            "org.springframework.",
            "jakarta.",
            "javax.",
            "org.hibernate.",
            "org.mapstruct.",
            "com.fasterxml.",
            "dev.langchain4j.",
            "io.swagger.",
            "org.slf4j.",
            "com.travelplanner.api.",
            "com.travelplanner.application.",
            "com.travelplanner.infrastructure.",
            "com.travelplanner.ai.",
            "com.travelplanner.config.");

    @Test
    void domainSourcesImportNoFrameworkVendorOrOuterLayerType() {
        List<Path> sources = domainSources();

        // Guards the guard: an empty scan would pass silently if the path ever moved.
        assertThat(sources).as("domain sources under %s", DOMAIN).isNotEmpty();

        for (Path source : sources) {
            for (String line : readLines(source)) {
                String trimmed = line.strip();
                if (!trimmed.startsWith("import ")) {
                    continue;
                }
                String imported = trimmed.substring("import ".length()).replace("static ", "");
                assertThat(FORBIDDEN_IMPORT_PREFIXES)
                        .describedAs("%s imports %s — the domain must stay framework-free "
                                + "(PLAN §4.0.2-F)", source.getFileName(), imported)
                        .noneMatch(imported::startsWith);
            }
        }
    }

    private static List<Path> domainSources() {
        try (Stream<Path> paths = Files.walk(DOMAIN)) {
            return paths.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }

    private static List<String> readLines(Path source) {
        try {
            return Files.readAllLines(source);
        } catch (IOException unreadable) {
            throw new UncheckedIOException(unreadable);
        }
    }
}
