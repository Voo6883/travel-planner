package com.travelplanner.infrastructure.knowledge;

import java.util.List;

/**
 * CI entry point for seed validation (task 17).
 *
 * <p>Invoked as {@code ./gradlew validateKnowledgeSeed}. Exit code 0 means the classpath sample
 * seed is structurally sound; any quality finding prints to stderr and exits 1.
 */
public final class ValidateKnowledgeSeed {

    private ValidateKnowledgeSeed() {
    }

    public static void main(String[] args) {
        List<String> errors = new SampleKnowledgeValidator().validateAll();
        if (errors.isEmpty()) {
            System.out.println("Knowledge seed validation OK ("
                    + SampleKnowledgeReader.DESTINATION_SLUGS.size() + " destinations)");
            return;
        }
        System.err.println("Knowledge seed validation FAILED (" + errors.size() + " issue(s)):");
        for (String error : errors) {
            System.err.println("  - " + error);
        }
        System.exit(1);
    }
}
