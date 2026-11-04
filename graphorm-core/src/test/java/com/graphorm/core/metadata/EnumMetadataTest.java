package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EnumMetadata Unit Tests")
class EnumMetadataTest {

    @Test
    @DisplayName("Should build enum metadata with defensive copy and immutability")
    void shouldBuildEnumMetadataWithDefensiveCopy() {
        List<String> valuesList = new ArrayList<>(List.of("ACTIVE", "INACTIVE", "SUSPENDED"));

        EnumMetadata enumMeta = EnumMetadata.builder("AccountStatus")
                .values(valuesList)
                .build();

        assertThat(enumMeta.enumName()).isEqualTo("AccountStatus");
        assertThat(enumMeta.values()).containsExactly("ACTIVE", "INACTIVE", "SUSPENDED");

        // Mutating input list should not affect metadata
        valuesList.add("MALICIOUS");
        assertThat(enumMeta.values()).doesNotContain("MALICIOUS");

        // Mutating returned values must throw UnsupportedOperationException
        assertThatThrownBy(() -> enumMeta.values().add("MALICIOUS"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should test hasValue method correctly")
    void shouldTestHasValue() {
        EnumMetadata enumMeta = EnumMetadata.builder("Role")
                .values("ADMIN", "USER", "GUEST")
                .build();

        assertThat(enumMeta.hasValue("ADMIN")).isTrue();
        assertThat(enumMeta.hasValue("USER")).isTrue();
        assertThat(enumMeta.hasValue("ROOT")).isFalse();
        assertThat(enumMeta.hasValue(null)).isFalse();
    }

    @Test
    @DisplayName("Should reject invalid enum names and empty values")
    void shouldRejectInvalidArguments() {
        assertThatThrownBy(() -> EnumMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new EnumMetadata(" ", List.of("A")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new EnumMetadata("Role", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Enum values must not be empty");

        assertThatThrownBy(() -> new EnumMetadata("Role", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        EnumMetadata e1 = EnumMetadata.builder("Status").values("A", "B").build();
        EnumMetadata e2 = EnumMetadata.builder("Status").values("A", "B").build();

        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
        assertThat(e1.toString()).contains("Status", "A", "B");
    }
}
