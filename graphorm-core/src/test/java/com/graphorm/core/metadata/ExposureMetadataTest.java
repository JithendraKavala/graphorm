package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ExposureMetadata Unit Tests")
class ExposureMetadataTest {

    @Test
    @DisplayName("Should verify preset exposure configurations")
    void testPresets() {
        assertThat(ExposureMetadata.all().queries()).isTrue();
        assertThat(ExposureMetadata.all().mutations()).isTrue();

        assertThat(ExposureMetadata.queriesOnly().queries()).isTrue();
        assertThat(ExposureMetadata.queriesOnly().mutations()).isFalse();

        assertThat(ExposureMetadata.mutationsOnly().queries()).isFalse();
        assertThat(ExposureMetadata.mutationsOnly().mutations()).isTrue();

        assertThat(ExposureMetadata.none().queries()).isFalse();
        assertThat(ExposureMetadata.none().mutations()).isFalse();
    }

    @Test
    @DisplayName("Should verify ModelMetadata and ViewModelMetadata exposure integration")
    void testModelExposure() {
        ModelMetadata model = ModelMetadata.builder("SensitiveData")
                .tableName("sensitive_data")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .expose(true, false) // queries only
                .build();

        assertThat(model.exposeQueries()).isTrue();
        assertThat(model.exposeMutations()).isFalse();

        ViewModelMetadata view = ViewModelMetadata.builder("SensitiveSummary")
                .viewName("vw_sensitive_summary")
                .addField(FieldMetadata.builder("count", Integer.class).build())
                .exposeQueries(false)
                .build();

        assertThat(view.exposeQueries()).isFalse();
        assertThat(view.exposeMutations()).isFalse();
    }
}
