package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Definition of a database table for DDL generation and diffing.
 *
 * @param name              physical table name
 * @param schemaName        database schema/namespace (e.g. "public")
 * @param columns           column definitions
 * @param primaryKeyColumns column names forming the primary key
 * @param uniqueConstraints compound unique constraint column sets
 * @param indexes           secondary indexes
 * @param foreignKeys       foreign key definitions
 * @param checkConstraints  check constraints
 */
public record TableDefinition(
        String name,
        String schemaName,
        List<ColumnDefinition> columns,
        List<String> primaryKeyColumns,
        List<List<String>> uniqueConstraints,
        List<IndexMetadata> indexes,
        List<ForeignKeyDefinition> foreignKeys,
        List<CheckConstraintMetadata> checkConstraints
) {

    public TableDefinition {
        Objects.requireNonNull(name, "Table name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Table name must not be blank");
        }
        if (schemaName != null && schemaName.isBlank()) {
            schemaName = null;
        }
        columns = columns == null ? List.of() : List.copyOf(columns);
        primaryKeyColumns = primaryKeyColumns == null ? List.of() : List.copyOf(primaryKeyColumns);
        uniqueConstraints = uniqueConstraints == null ? List.of() : List.copyOf(uniqueConstraints.stream().map(List::copyOf).toList());
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        foreignKeys = foreignKeys == null ? List.of() : List.copyOf(foreignKeys);
        checkConstraints = checkConstraints == null ? List.of() : List.copyOf(checkConstraints);
    }

    public Optional<ColumnDefinition> findColumn(String columnName) {
        if (columnName == null) {
            return Optional.empty();
        }
        return columns.stream()
                .filter(c -> c.name().equalsIgnoreCase(columnName))
                .findFirst();
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private String schemaName;
        private final List<ColumnDefinition> columns = new ArrayList<>();
        private final List<String> primaryKeyColumns = new ArrayList<>();
        private final List<List<String>> uniqueConstraints = new ArrayList<>();
        private final List<IndexMetadata> indexes = new ArrayList<>();
        private final List<ForeignKeyDefinition> foreignKeys = new ArrayList<>();
        private final List<CheckConstraintMetadata> checkConstraints = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Table name must not be null");
        }

        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder addColumn(ColumnDefinition column) {
            this.columns.add(Objects.requireNonNull(column, "Column must not be null"));
            return this;
        }

        public Builder columns(List<ColumnDefinition> columns) {
            this.columns.clear();
            if (columns != null) {
                this.columns.addAll(columns);
            }
            return this;
        }

        public Builder addPrimaryKeyColumn(String column) {
            this.primaryKeyColumns.add(Objects.requireNonNull(column, "Primary key column must not be null"));
            return this;
        }

        public Builder primaryKeyColumns(List<String> primaryKeyColumns) {
            this.primaryKeyColumns.clear();
            if (primaryKeyColumns != null) {
                this.primaryKeyColumns.addAll(primaryKeyColumns);
            }
            return this;
        }

        public Builder addUniqueConstraint(List<String> columns) {
            this.uniqueConstraints.add(List.copyOf(columns));
            return this;
        }

        public Builder uniqueConstraints(List<List<String>> uniqueConstraints) {
            this.uniqueConstraints.clear();
            if (uniqueConstraints != null) {
                this.uniqueConstraints.addAll(uniqueConstraints);
            }
            return this;
        }

        public Builder addIndex(IndexMetadata index) {
            this.indexes.add(Objects.requireNonNull(index, "Index must not be null"));
            return this;
        }

        public Builder indexes(List<IndexMetadata> indexes) {
            this.indexes.clear();
            if (indexes != null) {
                this.indexes.addAll(indexes);
            }
            return this;
        }

        public Builder addForeignKey(ForeignKeyDefinition fk) {
            this.foreignKeys.add(Objects.requireNonNull(fk, "Foreign key must not be null"));
            return this;
        }

        public Builder foreignKeys(List<ForeignKeyDefinition> foreignKeys) {
            this.foreignKeys.clear();
            if (foreignKeys != null) {
                this.foreignKeys.addAll(foreignKeys);
            }
            return this;
        }

        public Builder addCheckConstraint(CheckConstraintMetadata checkConstraint) {
            this.checkConstraints.add(Objects.requireNonNull(checkConstraint, "Check constraint must not be null"));
            return this;
        }

        public Builder checkConstraints(List<CheckConstraintMetadata> checkConstraints) {
            this.checkConstraints.clear();
            if (checkConstraints != null) {
                this.checkConstraints.addAll(checkConstraints);
            }
            return this;
        }

        public TableDefinition build() {
            return new TableDefinition(name, schemaName, columns, primaryKeyColumns, uniqueConstraints, indexes, foreignKeys, checkConstraints);
        }
    }
}
