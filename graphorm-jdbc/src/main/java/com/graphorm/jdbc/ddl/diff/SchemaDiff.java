package com.graphorm.jdbc.ddl.diff;

import com.graphorm.jdbc.ddl.TableDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level difference between two database schemas.
 */
public record SchemaDiff(
        List<TableDefinition> createdTables,
        List<TableDefinition> droppedTables,
        List<TableDiff> alteredTables,
        Map<String, List<String>> createdEnums,
        List<String> droppedEnums,
        List<EnumDiff> alteredEnums
) {

    public SchemaDiff {
        createdTables = createdTables == null ? List.of() : List.copyOf(createdTables);
        droppedTables = droppedTables == null ? List.of() : List.copyOf(droppedTables);
        alteredTables = alteredTables == null ? List.of() : List.copyOf(alteredTables);
        createdEnums = createdEnums == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(createdEnums));
        droppedEnums = droppedEnums == null ? List.of() : List.copyOf(droppedEnums);
        alteredEnums = alteredEnums == null ? List.of() : List.copyOf(alteredEnums);
    }

    public boolean isEmpty() {
        return createdTables.isEmpty()
                && droppedTables.isEmpty()
                && alteredTables.isEmpty()
                && createdEnums.isEmpty()
                && droppedEnums.isEmpty()
                && alteredEnums.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<TableDefinition> createdTables = new ArrayList<>();
        private final List<TableDefinition> droppedTables = new ArrayList<>();
        private final List<TableDiff> alteredTables = new ArrayList<>();
        private final Map<String, List<String>> createdEnums = new LinkedHashMap<>();
        private final List<String> droppedEnums = new ArrayList<>();
        private final List<EnumDiff> alteredEnums = new ArrayList<>();

        public Builder addCreatedTable(TableDefinition table) {
            this.createdTables.add(table);
            return this;
        }

        public Builder addDroppedTable(TableDefinition table) {
            this.droppedTables.add(table);
            return this;
        }

        public Builder addAlteredTable(TableDiff diff) {
            this.alteredTables.add(diff);
            return this;
        }

        public Builder addCreatedEnum(String name, List<String> values) {
            this.createdEnums.put(name, values != null ? List.copyOf(values) : List.of());
            return this;
        }

        public Builder addDroppedEnum(String name) {
            this.droppedEnums.add(name);
            return this;
        }

        public Builder addAlteredEnum(EnumDiff diff) {
            this.alteredEnums.add(diff);
            return this;
        }

        public SchemaDiff build() {
            return new SchemaDiff(createdTables, droppedTables, alteredTables, createdEnums, droppedEnums, alteredEnums);
        }
    }
}
