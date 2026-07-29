package com.travelplanner.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.domain.enums.KnowledgeMatchType;
import com.travelplanner.domain.valueobject.KnowledgeQuery;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Guards on the hybrid SQL path that do not need a live database (task 17). */
class KnowledgeVectorSearchTest {

    @Test
    void rejectsAnUnsafeDestinationSlugBeforeBuildingSql() {
        KnowledgeVectorSearch search = new KnowledgeVectorSearch();
        KnowledgeQuery query = new KnowledgeQuery(
                UUID.randomUUID(),
                "temples",
                new float[KnowledgeQuery.EMBEDDING_DIMENSION],
                5,
                0.5,
                Set.of(KnowledgeMatchType.POI));

        assertThatThrownBy(() -> search.search(query, "tokyo-jp';drop table"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe destination slug");
        assertThatThrownBy(() -> search.search(query, "Tokyo"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe destination slug");
    }
}
