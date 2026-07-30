package com.travelplanner.ai.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and writes the recorded fixtures a {@link ReplayLlmAdapter} serves (review §6.I).
 *
 * <p>One JSON file per exchange, named for its prompt hash, in a directory the caller chooses. One
 * file per exchange rather than one big index: fixtures are committed, and a single file means a
 * recording appears in a diff as an addition rather than as a hunk somewhere inside a 4,000-line array
 * that nobody reads.
 *
 * <p><strong>Loaded eagerly, once.</strong> Replay happens on the request path, and reading the disk
 * per call would put a filesystem stat into a code path whose entire purpose is to be fast and
 * deterministic. Eager loading also means a malformed fixture fails at startup with the filename,
 * rather than mid-test with a stack trace in Jackson.
 *
 * <p>A missing directory is not an error. Replay is opt-in, the fixtures live under
 * {@code src/test/resources} in the normal case, and a checkout that has never recorded anything should
 * start — {@link ReplayLlmAdapter} is where an unmatched call is refused, and it refuses with a message
 * that says how to record one.
 */
public final class RecordedExchangeStore {

    private static final Logger log = LoggerFactory.getLogger(RecordedExchangeStore.class);

    private static final String EXTENSION = ".json";

    private final Path directory;
    private final ObjectMapper objectMapper;
    private final Map<String, RecordedExchange> byPromptHash;

    public RecordedExchangeStore(Path directory, ObjectMapper objectMapper) {
        this.directory = directory;
        this.objectMapper = objectMapper;
        this.byPromptHash = load(directory, objectMapper);
        log.info("AI replay: {} recorded exchange(s) from {}", byPromptHash.size(), directory);
    }

    public Optional<RecordedExchange> find(String promptHash) {
        return Optional.ofNullable(byPromptHash.get(promptHash));
    }

    public int size() {
        return byPromptHash.size();
    }

    /** Every loaded hash, for the "no fixture" message and for a recording-coverage report. */
    public List<String> promptHashes() {
        return List.copyOf(byPromptHash.keySet());
    }

    /**
     * Writes one recording, keyed by its prompt hash, and adds it to the in-memory index.
     *
     * <p>Overwrites deliberately: re-recording after a prompt change is the intended workflow, and a
     * store that refused would make the fix "delete the file first", which somebody does with a
     * wildcard. The prompt hash is in the filename, so an overwrite can only ever replace a recording
     * of the same prompt.
     */
    public void save(RecordedExchange exchange) {
        try {
            Files.createDirectories(directory);
            Path file = directory.resolve(exchange.promptHash() + EXTENSION);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), exchange);
            byPromptHash.put(exchange.promptHash(), exchange);
            log.info("AI replay: recorded {} ({})", file.getFileName(), exchange.provider());
        } catch (IOException failure) {
            throw new UncheckedIOException("could not write the recorded exchange", failure);
        }
    }

    /**
     * @throws IllegalStateException naming the file, when a fixture will not parse. Deliberately fatal:
     *     a replay suite that silently skipped a broken recording would report a "no fixture" failure
     *     for a fixture that is sitting right there, and somebody would spend an afternoon on it
     */
    private static Map<String, RecordedExchange> load(Path directory, ObjectMapper objectMapper) {
        Map<String, RecordedExchange> loaded = new LinkedHashMap<>();
        if (!Files.isDirectory(directory)) {
            return loaded;
        }
        for (Path file : jsonFiles(directory)) {
            RecordedExchange exchange = read(file, objectMapper);
            // The filename IS the key, so a mismatch means the file was renamed or hand-edited — and a
            // recording that answers to a hash it does not carry is one that replays for the wrong
            // prompt. Refuse rather than trust either half.
            String fileName = file.getFileName().toString();
            String expected = fileName.substring(0, fileName.length() - EXTENSION.length());
            if (!expected.equals(exchange.promptHash())) {
                throw new IllegalStateException("recorded exchange " + file + " declares promptHash "
                        + exchange.promptHash() + ", which does not match its filename. Rename the file "
                        + "or re-record; a fixture that answers to the wrong prompt is worse than none.");
            }
            loaded.put(exchange.promptHash(), exchange);
        }
        return loaded;
    }

    private static List<Path> jsonFiles(Path directory) {
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> sorted = new ArrayList<>(files
                    .filter(path -> path.getFileName().toString().endsWith(EXTENSION))
                    .toList());
            sorted.sort(Path::compareTo);
            return sorted;
        } catch (IOException failure) {
            throw new UncheckedIOException("could not list " + directory, failure);
        }
    }

    private static RecordedExchange read(Path file, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(Files.readString(file, StandardCharsets.UTF_8),
                    RecordedExchange.class);
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("could not read the recorded exchange " + file, failure);
        }
    }
}
