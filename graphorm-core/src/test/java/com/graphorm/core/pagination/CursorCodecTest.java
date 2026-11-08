package com.graphorm.core.pagination;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CursorCodec Unit Tests")
class CursorCodecTest {

    @Test
    @DisplayName("Should encode and decode single value cursor")
    void testSingleValue() {
        String cursor = CursorCodec.encode("user-123");
        assertThat(cursor).isNotBlank();

        Object decoded = CursorCodec.decodeSingle(cursor);
        assertThat(decoded).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Should encode and decode composite keyset tuple (timestamp, id)")
    void testCompositeTuple() {
        long timestamp = 1700000000000L;
        long id = 42L;

        String cursor = CursorCodec.encode(timestamp, id);
        assertThat(cursor).isNotBlank();

        List<Object> decoded = CursorCodec.decode(cursor);
        assertThat(decoded).containsExactly(timestamp, id);
    }

    @Test
    @DisplayName("Should encode and decode multiple heterogeneous types")
    void testHeterogeneousTypes() {
        String cursor = CursorCodec.encode("Alice", 100L, 98.5, true);
        assertThat(cursor).isNotBlank();

        List<Object> decoded = CursorCodec.decode(cursor);
        assertThat(decoded).hasSize(4);
        assertThat(decoded.get(0)).isEqualTo("Alice");
        assertThat(decoded.get(1)).isEqualTo(100L);
        assertThat(decoded.get(2)).isEqualTo(98.5);
        assertThat(decoded.get(3)).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException on null or malformed cursor")
    void testMalformedCursor() {
        assertThatThrownBy(() -> CursorCodec.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CursorCodec.decode(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CursorCodec.decode("not-valid-base64!@#$"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when encoding empty values")
    void testEmptyValues() {
        assertThatThrownBy(() -> CursorCodec.encode(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
