package com.graphorm.jdbc.relation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BatchLoadResult Unit Tests")
class BatchLoadResultTest {

    @Test
    @DisplayName("Should permit null values in oneToOne map for nullable associations")
    void shouldPermitNullValuesInMap() {
        Map<String, String> mapWithNull = new HashMap<>();
        mapWithNull.put("user-1", "Profile 1");
        mapWithNull.put("user-2", null); // User 2 has no profile (nullable association)

        BatchLoadResult<String, String> result = new BatchLoadResult<>(mapWithNull, Map.of(), Map.of());

        assertThat(result.oneToOne()).containsKey("user-2");
        assertThat(result.getOne("user-2")).isEmpty();
        assertThat(result.getOne("user-1")).contains("Profile 1");
        assertThatThrownBy(() -> result.oneToOne().put("user-3", "Profile 3"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
