package com.travelplanner.application.page;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.travelplanner.api.dto.page.PageMetadata;
import com.travelplanner.domain.exception.ValidationFailedException;
import org.junit.jupiter.api.Test;

/** Pagination conventions (PLAN §6.1). */
class PageQueryTest {

    @Test
    void appliesThePublishedDefaultsWhenNothingIsSupplied() {
        PageQuery query = PageQuery.of(null, null, null);

        assertThat(query.page()).isZero();
        assertThat(query.pageSize()).isEqualTo(20);
        assertThat(query.sort()).isEqualTo("-created_at");
    }

    @Test
    void aLeadingMinusMeansDescending() {
        assertThat(PageQuery.of(null, null, "-created_at").descending()).isTrue();
        assertThat(PageQuery.of(null, null, "-created_at").sortField()).isEqualTo("created_at");
        assertThat(PageQuery.of(null, null, "name").descending()).isFalse();
        assertThat(PageQuery.of(null, null, "name").sortField()).isEqualTo("name");
    }

    @Test
    void rejectsAPageSizeAboveTheCeilingInsteadOfClamping() {
        // Clamping makes a client's paging arithmetic wrong in a way it cannot detect.
        assertThatThrownBy(() -> PageQuery.of(0, 101, null))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(thrown -> assertThat(((ValidationFailedException) thrown).code())
                        .isEqualTo("validation_failed"));
    }

    @Test
    void rejectsNegativePagesAndEmptyPages() {
        assertThatThrownBy(() -> PageQuery.of(-1, null, null))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> PageQuery.of(0, 0, null))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void rejectsSortValuesOutsideThePublishedPattern() {
        assertThatThrownBy(() -> PageQuery.of(null, null, "createdAt"))
                .isInstanceOf(ValidationFailedException.class);
        assertThatThrownBy(() -> PageQuery.of(null, null, "created_at; DROP TABLE trip"))
                .isInstanceOf(ValidationFailedException.class);
    }

    @Test
    void reportsTheWireFieldNameSoTheFrontendCanHighlightTheInput() {
        assertThatThrownBy(() -> PageQuery.of(0, 101, null))
                .isInstanceOf(ValidationFailedException.class)
                .satisfies(thrown -> assertThat(((ValidationFailedException) thrown).details())
                        .hasToString("{fields={page_size=must be between 1 and 100}}"));
    }

    @Test
    void offsetIsComputedInLongArithmetic() {
        // page * pageSize overflows int well inside the range a client can request.
        assertThat(PageQuery.of(30_000_000, 100, null).offset()).isEqualTo(3_000_000_000L);
    }

    @Test
    void metadataEchoesTheQueryAlongsideTheTotal() {
        PageMetadata metadata = PageMetadata.of(PageQuery.of(2, 50, null), 142L);

        assertThat(metadata.page()).isEqualTo(2);
        assertThat(metadata.pageSize()).isEqualTo(50);
        assertThat(metadata.total()).isEqualTo(142L);
    }
}
