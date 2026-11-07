package com.graphorm.graphql.response;

import com.graphorm.core.query.ProjectedColumn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ResponseAssembler Unit Tests (TASK-11.3)")
class ResponseAssemblerTest {

    @Test
    @DisplayName("TASK-11.3: Strips hidden fields from single row map")
    void shouldStripHiddenFieldsFromSingleMap() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Alice");
        row.put("email", "alice@example.com");
        row.put("departmentId", 1L);
        row.put("department_id", 1L);

        Map<String, Object> cleaned = ResponseAssembler.stripHidden(row, Set.of("departmentId", "department_id"));

        assertThat(cleaned).containsOnlyKeys("name", "email");
        assertThat(cleaned.get("name")).isEqualTo("Alice");
        assertThat(cleaned.get("email")).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("TASK-11.3: Strips hidden fields from list of row maps")
    void shouldStripHiddenFieldsFromList() {
        Map<String, Object> row1 = new HashMap<>();
        row1.put("name", "Alice");
        row1.put("departmentId", 1L);

        Map<String, Object> row2 = new HashMap<>();
        row2.put("name", "Bob");
        row2.put("departmentId", 2L);

        List<Map<String, Object>> cleaned = ResponseAssembler.stripHidden(List.of(row1, row2), Set.of("departmentId"));

        assertThat(cleaned).hasSize(2);
        assertThat(cleaned.get(0)).containsOnlyKeys("name");
        assertThat(cleaned.get(1)).containsOnlyKeys("name");
    }

    @Test
    @DisplayName("TASK-11.3: Strips columns based on ProjectedColumn.isHiddenDependency flag")
    void shouldStripColumnsBasedOnHiddenFlag() {
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Alice");
        row.put("department_id", 1L);

        List<ProjectedColumn> columns = List.of(
                ProjectedColumn.of("name", null),
                ProjectedColumn.hidden("department_id", null)
        );

        Map<String, Object> cleaned = ResponseAssembler.stripHiddenDependencies(row, columns);

        assertThat(cleaned).containsOnlyKeys("name");
        assertThat(cleaned.get("name")).isEqualTo("Alice");
    }

    @Test
    @DisplayName("Handles null and empty inputs safely")
    void shouldHandleNullAndEmptySafely() {
        Map<String, Object> nullMap = null;
        List<Map<String, Object>> nullList = null;

        assertThat(ResponseAssembler.stripHidden(nullMap, Set.of("a"))).isNull();
        assertThat(ResponseAssembler.stripHidden(Map.of("a", 1), null)).isEqualTo(Map.of("a", 1));
        assertThat(ResponseAssembler.stripHidden(Map.of("a", 1), Set.of())).isEqualTo(Map.of("a", 1));

        assertThat(ResponseAssembler.stripHidden(nullList, Set.of())).isEmpty();
        assertThat(ResponseAssembler.stripHiddenDependencies(nullMap, List.of())).isNull();
    }
}
