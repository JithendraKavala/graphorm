package com.graphorm.graphql.response;

import com.graphorm.core.query.ProjectedColumn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Assembles and cleans GraphQL responses, ensuring that hidden dependency columns
 * (e.g. foreign keys auto-injected for downstream relationship loading or view join calculations)
 * are stripped before final client delivery (TASK-11.3, DOC-01).
 * <p>
 * In the GraphQL engine:
 * <ul>
 *   <li>When downstream relationship fields are absent, DataFetchers invoke {@link #stripHidden} directly
 *       to remove hidden columns before returning from the fetcher.</li>
 *   <li>When downstream relations are present, intermediate maps retain foreign keys so child
 *       DataFetchers (e.g. {@code ModelRelationDataFetcher}) can extract join keys from {@code env.getSource()}.
 *       GraphQL Java's execution strategy then guarantees that only client-requested fields are serialized
 *       into the final JSON response.</li>
 * </ul>
 */
public final class ResponseAssembler {

    private ResponseAssembler() {}

    /**
     * Strips hidden dependency fields from a single entity result map.
     *
     * @param row          the entity row map
     * @param hiddenFields the set of field/column names to remove
     * @return a new map containing only visible fields
     */
    public static Map<String, Object> stripHidden(Map<String, Object> row, Set<String> hiddenFields) {
        if (row == null || hiddenFields == null || hiddenFields.isEmpty()) {
            return row;
        }

        Map<String, Object> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (!hiddenFields.contains(entry.getKey())) {
                cleaned.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(cleaned);
    }

    /**
     * Strips hidden dependency fields from a list of entity result maps.
     *
     * @param rows         the list of entity row maps
     * @param hiddenFields the set of field/column names to remove
     * @return a list of maps containing only visible fields
     */
    public static List<Map<String, Object>> stripHidden(List<Map<String, Object>> rows, Set<String> hiddenFields) {
        if (rows == null || rows.isEmpty() || hiddenFields == null || hiddenFields.isEmpty()) {
            return rows != null ? rows : List.of();
        }

        List<Map<String, Object>> cleaned = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            cleaned.add(stripHidden(row, hiddenFields));
        }
        return Collections.unmodifiableList(cleaned);
    }

    /**
     * Strips all columns marked as {@code isHiddenDependency = true} in the projected column list.
     *
     * @param row     the entity row map
     * @param columns the projected column list from SelectQuery
     * @return cleaned row map
     */
    public static Map<String, Object> stripHiddenDependencies(Map<String, Object> row, Collection<ProjectedColumn> columns) {
        if (row == null || columns == null || columns.isEmpty()) {
            return row;
        }

        Set<String> hiddenNames = columns.stream()
                .filter(ProjectedColumn::isHiddenDependency)
                .map(c -> c.alias() != null ? c.alias() : c.expression())
                .collect(Collectors.toSet());

        return stripHidden(row, hiddenNames);
    }

    /**
     * Strips all columns marked as {@code isHiddenDependency = true} from a list of rows.
     */
    public static List<Map<String, Object>> stripHiddenDependencies(List<Map<String, Object>> rows, Collection<ProjectedColumn> columns) {
        if (rows == null || rows.isEmpty() || columns == null || columns.isEmpty()) {
            return rows != null ? rows : List.of();
        }

        Set<String> hiddenNames = columns.stream()
                .filter(ProjectedColumn::isHiddenDependency)
                .map(c -> c.alias() != null ? c.alias() : c.expression())
                .collect(Collectors.toSet());

        return stripHidden(rows, hiddenNames);
    }
}
