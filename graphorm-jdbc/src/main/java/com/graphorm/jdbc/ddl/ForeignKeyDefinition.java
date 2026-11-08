package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.ReferentialAction;

import java.util.List;
import java.util.Objects;

/**
 * Definition of a foreign key constraint for DDL generation and diffing.
 *
 * @param name          the foreign key constraint name
 * @param sourceTable   the source physical table name
 * @param sourceColumns the source column names
 * @param targetTable   the referenced target table name
 * @param targetColumns the referenced target column names
 * @param onDelete      referential action on delete
 * @param onUpdate      referential action on update
 */
public record ForeignKeyDefinition(
        String name,
        String sourceTable,
        List<String> sourceColumns,
        String targetTable,
        List<String> targetColumns,
        ReferentialAction onDelete,
        ReferentialAction onUpdate
) {

    public ForeignKeyDefinition {
        Objects.requireNonNull(name, "Foreign key name must not be null");
        Objects.requireNonNull(sourceTable, "Source table must not be null");
        Objects.requireNonNull(sourceColumns, "Source columns must not be null");
        Objects.requireNonNull(targetTable, "Target table must not be null");
        Objects.requireNonNull(targetColumns, "Target columns must not be null");
        if (sourceColumns.isEmpty()) {
            throw new IllegalArgumentException("Source columns must not be empty");
        }
        if (sourceColumns.size() != targetColumns.size()) {
            throw new IllegalArgumentException("Source and target columns size mismatch");
        }
        sourceColumns = List.copyOf(sourceColumns);
        targetColumns = List.copyOf(targetColumns);
        onDelete = onDelete != null ? onDelete : ReferentialAction.NO_ACTION;
        onUpdate = onUpdate != null ? onUpdate : ReferentialAction.NO_ACTION;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String sourceTable;
        private List<String> sourceColumns = List.of();
        private String targetTable;
        private List<String> targetColumns = List.of();
        private ReferentialAction onDelete = ReferentialAction.NO_ACTION;
        private ReferentialAction onUpdate = ReferentialAction.NO_ACTION;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Foreign key name must not be null");
        }

        public Builder sourceTable(String sourceTable) {
            this.sourceTable = Objects.requireNonNull(sourceTable, "Source table must not be null");
            return this;
        }

        public Builder sourceColumns(List<String> sourceColumns) {
            this.sourceColumns = sourceColumns != null ? List.copyOf(sourceColumns) : List.of();
            return this;
        }

        public Builder targetTable(String targetTable) {
            this.targetTable = Objects.requireNonNull(targetTable, "Target table must not be null");
            return this;
        }

        public Builder targetColumns(List<String> targetColumns) {
            this.targetColumns = targetColumns != null ? List.copyOf(targetColumns) : List.of();
            return this;
        }

        public Builder onDelete(ReferentialAction onDelete) {
            this.onDelete = onDelete != null ? onDelete : ReferentialAction.NO_ACTION;
            return this;
        }

        public Builder onUpdate(ReferentialAction onUpdate) {
            this.onUpdate = onUpdate != null ? onUpdate : ReferentialAction.NO_ACTION;
            return this;
        }

        public ForeignKeyDefinition build() {
            return new ForeignKeyDefinition(name, sourceTable, sourceColumns, targetTable, targetColumns, onDelete, onUpdate);
        }
    }
}
