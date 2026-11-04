package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CompositeTypeMetadata Unit Tests")
class CompositeTypeMetadataTest {

    @Test
    @DisplayName("Should build composite type metadata with mutation defense")
    void shouldBuildCompositeTypeWithMutationDefense() {
        List<FieldMetadata> fields = new ArrayList<>(List.of(
                FieldMetadata.builder("street", String.class).build(),
                FieldMetadata.builder("city", String.class).build(),
                FieldMetadata.builder("zipCode", String.class).build()
        ));

        CompositeTypeMetadata composite = CompositeTypeMetadata.builder("Address")
                .fields(fields)
                .build();

        assertThat(composite.name()).isEqualTo("Address");
        assertThat(composite.entityType()).isEqualTo(EntityType.COMPOSITE_TYPE);
        assertThat(composite.isReadOnly()).isFalse();
        assertThat(composite.fields()).hasSize(3);
        assertThat(composite.hasField("city")).isTrue();
        assertThat(composite.getField("city").javaType()).isEqualTo(String.class);

        // Mutating source list
        fields.clear();
        assertThat(composite.fields()).hasSize(3);

        // Mutating returned list
        assertThatThrownBy(() -> composite.fields().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should reject invalid composite type names")
    void shouldRejectInvalidName() {
        assertThatThrownBy(() -> CompositeTypeMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new CompositeTypeMetadata("  ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        CompositeTypeMetadata c1 = CompositeTypeMetadata.builder("Geo").build();
        CompositeTypeMetadata c2 = CompositeTypeMetadata.builder("Geo").build();

        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        assertThat(c1.toString()).contains("Geo");
    }
}
