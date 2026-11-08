package com.graphorm.jdbc.ddl.diff;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Captures modifications to an existing table.
 */
public record TableDiff(
        String tableName,
        List<ColumnDefinition> addedColumns,
        List<ColumnDefinition> droppedColumns,
        List<ColumnDiff> alteredColumns,
        List<ForeignKeyDefinition> addedForeignKeys,
        List<String> droppedForeignKeyNames,
        List<List<String>> addedUniqueConstraints,
        List<String> droppedUniqueConstraintNames,
        List<IndexMetadata> addedIndexes,
        List<String> droppedIndexNames,
        List<CheckConstraintMetadata> addedCheckConstraints,
        List<String> droppedCheckConstraintNames
) {

    public TableDiff {
        Objects.requireNonNull(tableName, "Table name must not be null");
        addedColumns = addedColumns == null ? List.of() : List.copyOf(addedColumns);
        droppedColumns = droppedColumns == null ? List.of() : List.copyOf(droppedColumns);
        alteredColumns = alteredColumns == null ? List.of() : List.copyOf(alteredColumns);
        addedForeignKeys = addedForeignKeys == null ? List.of() : List.copyOf(addedForeignKeys);
        droppedForeignKeyNames = droppedForeignKeyNames == null ? List.of() : List.copyOf(droppedForeignKeyNames);
        addedUniqueConstraints = addedUniqueConstraints == null ? List.of() : List.copyOf(addedUniqueConstraints);
        droppedUniqueConstraintNames = droppedUniqueConstraintNames == null ? List.of() : List.copyOf(droppedUniqueConstraintNames);
        addedIndexes = addedIndexes == null ? List.of() : List.copyOf(addedIndexes);
        droppedIndexNames = droppedIndexNames == null ? List.of() : List.copyOf(droppedIndexNames);
        addedCheckConstraints = addedCheckConstraints == null ? List.of() : List.copyOf(addedCheckConstraints);
        droppedCheckConstraintNames = droppedCheckConstraintNames == null ? List.of() : List.copyOf(droppedCheckConstraintNames);
    }

    public boolean isEmpty() {
        return addedColumns.isEmpty()
                && droppedColumns.isEmpty()
                && alteredColumns.isEmpty()
                && addedForeignKeys.isEmpty()
                && droppedForeignKeyNames.isEmpty()
                && addedUniqueConstraints.isEmpty()
                && droppedUniqueConstraintNames.isEmpty()
                && addedIndexes.isEmpty()
                && droppedIndexNames.isEmpty()
                && addedCheckConstraints.isEmpty()
                && droppedCheckConstraintNames.isEmpty();
    }

    public static Builder builder(String tableName) {
        return new Builder(tableName);
    }

    public static final class Builder {
        private final String tableName;
        private final List<ColumnDefinition> addedColumns = new ArrayList<>();
        private final List<ColumnDefinition> droppedColumns = new ArrayList<>();
        private final List<ColumnDiff> alteredColumns = new ArrayList<>();
        private final List<ForeignKeyDefinition> addedForeignKeys = new ArrayList<>();
        private final List<String> droppedForeignKeyNames = new ArrayList<>();
        private final List<List<String>> addedUniqueConstraints = new ArrayList<>();
        private final List<String> droppedUniqueConstraintNames = new ArrayList<>();
        private final List<IndexMetadata> addedIndexes = new ArrayList<>();
        private final List<String> droppedIndexNames = new ArrayList<>();
        private final List<CheckConstraintMetadata> addedCheckConstraints = new ArrayList<>();
        private final List<String> droppedCheckConstraintNames = new ArrayList<>();

        private Builder(String tableName) {
            this.tableName = Objects.requireNonNull(tableName, "Table name must not be null");
        }

        public Builder addColumn(ColumnDefinition col) {
            this.addedColumns.add(col);
            return this;
        }

        public Builder dropColumn(ColumnDefinition col) {
            this.droppedColumns.add(col);
            return this;
        }

        public Builder alterColumn(ColumnDiff diff) {
            this.alteredColumns.add(diff);
            return this;
        }

        public Builder addForeignKey(ForeignKeyDefinition fk) {
            this.addedForeignKeys.add(fk);
            return this;
        }

        public Builder dropForeignKey(String fkName) {
            this.droppedForeignKeyNames.add(fkName);
            return this;
        }

        public Builder addUniqueConstraint(List<String> cols) {
            this.addedUniqueConstraints.add(List.copyOf(cols));
            return this;
        }

        public Builder dropUniqueConstraint(String name) {
            this.droppedUniqueConstraintNames.add(name);
            return this;
        }

        public Builder addIndex(IndexMetadata index) {
            this.addedIndexes.add(index);
            return this;
        }

        public Builder dropIndex(String indexName) {
            this.droppedIndexNames.add(indexName);
            return this;
        }

        public Builder addCheckConstraint(CheckConstraintMetadata check) {
            this.addedCheckConstraints.add(check);
            return this;
        }

        public Builder dropCheckConstraint(String checkName) {
            this.droppedCheckConstraintNames.add(checkName);
            return this;
        }

        public TableDiff build() {
            return new TableDiff(
                    tableName,
                    addedColumns,
                    droppedColumns,
                    alteredColumns,
                    addedForeignKeys,
                    droppedForeignKeyNames,
                    addedUniqueConstraints,
                    droppedUniqueConstraintNames,
                    addedIndexes,
                    droppedIndexNames,
                    addedCheckConstraints,
                    droppedCheckConstraintNames
            );
        }
    }
}
