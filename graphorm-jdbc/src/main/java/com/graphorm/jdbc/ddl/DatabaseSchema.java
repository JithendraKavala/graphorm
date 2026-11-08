package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.EnumMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Neutral, immutable snapshot of a database schema containing tables, columns,
 * constraints, indexes, and enum definitions.
 */
public record DatabaseSchema(
        String schemaName,
        Map<String, TableDefinition> tables,
        Map<String, List<String>> enums
) {

    public DatabaseSchema {
        schemaName = (schemaName != null && !schemaName.isBlank()) ? schemaName : "public";
        tables = tables == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(tables));
        enums = enums == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(enums));
    }

    public Optional<TableDefinition> findTable(String tableName) {
        if (tableName == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, TableDefinition> entry : tables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(tableName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    public boolean hasTable(String tableName) {
        return findTable(tableName).isPresent();
    }

    public Optional<List<String>> findEnum(String enumName) {
        if (enumName == null) {
            return Optional.empty();
        }
        for (Map.Entry<String, List<String>> entry : enums.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(enumName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Builds a {@link DatabaseSchema} representation from an in-memory {@link ModelRegistry}.
     * Only regular models (non-view models) are transformed into tables.
     *
     * @param registry the compiled model registry
     * @return the corresponding DatabaseSchema
     */
    public static DatabaseSchema fromModelRegistry(ModelRegistry registry) {
        Objects.requireNonNull(registry, "ModelRegistry must not be null");

        Map<String, List<String>> enums = new LinkedHashMap<>();
        for (EnumMetadata enumMeta : registry.getAllEnums()) {
            enums.put(enumMeta.enumName(), List.copyOf(enumMeta.values()));
        }

        Map<String, TableDefinition> tables = new LinkedHashMap<>();

        for (ModelMetadata model : registry.getAllModels()) {
            TableDefinition.Builder tableBuilder = TableDefinition.builder(model.tableName());
            if (model.schemaName() != null) {
                tableBuilder.schemaName(model.schemaName());
            }

            // Columns
            for (FieldMetadata field : model.fields()) {
                if (field.isRelation() || field.isAggregate() || field.expression() != null) {
                    continue;
                }

                String sqlType = mapFieldTypeToSql(field, registry);
                boolean isGen = field.isGenerated()
                        || (field.defaultValue() != null && field.defaultValue().toLowerCase(Locale.ROOT).contains("autoincrement"));

                String defaultVal = null;
                if (!isGen && field.defaultValue() != null && !field.defaultValue().isBlank()) {
                    String raw = field.defaultValue().trim();
                    if (raw.equalsIgnoreCase("now()") || raw.equalsIgnoreCase("current_timestamp")) {
                        defaultVal = "CURRENT_TIMESTAMP";
                    } else if (raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("false")) {
                        defaultVal = raw.toUpperCase(Locale.ROOT);
                    } else {
                        defaultVal = raw;
                    }
                }

                ColumnDefinition col = ColumnDefinition.builder(field.dbColumnName())
                        .type(sqlType)
                        .nullable(field.isNullable())
                        .primaryKey(field.isPrimaryKey())
                        .unique(field.isUnique())
                        .generated(isGen)
                        .defaultValue(defaultVal)
                        .build();

                tableBuilder.addColumn(col);
            }

            // Primary Keys (mapped to dbColumnName)
            List<String> pkCols = model.primaryKeyFields().stream()
                    .map(f -> model.findField(f).map(FieldMetadata::dbColumnName).orElse(f))
                    .toList();
            tableBuilder.primaryKeyColumns(pkCols);

            // Unique constraints (mapped to dbColumnName)
            List<List<String>> uniqueCols = model.uniqueConstraints().stream()
                    .map(cols -> cols.stream()
                            .map(f -> model.findField(f).map(FieldMetadata::dbColumnName).orElse(f))
                            .toList())
                    .toList();
            tableBuilder.uniqueConstraints(uniqueCols);

            // Secondary Indexes (mapped to dbColumnName)
            List<IndexMetadata> mappedIndexes = model.indexes().stream()
                    .map(idx -> {
                        List<String> mappedCols = idx.fields().stream()
                                .map(f -> model.findField(f).map(FieldMetadata::dbColumnName).orElse(f))
                                .toList();
                        return IndexMetadata.builder(idx.name())
                                .fields(mappedCols)
                                .unique(idx.isUnique())
                                .type(idx.type())
                                .build();
                    })
                    .toList();
            tableBuilder.indexes(mappedIndexes);

            // Check Constraints
            tableBuilder.checkConstraints(model.checkConstraints());

            // Foreign Keys from Relations (mapped to dbColumnName)
            for (RelationMetadata rel : model.relations()) {
                if ((rel.relationType() == RelationType.MANY_TO_ONE || rel.relationType() == RelationType.ONE_TO_ONE)
                        && !rel.foreignKeyFields().isEmpty() && !rel.referenceFields().isEmpty()) {

                    ModelMetadata targetModel = registry.findModel(rel.targetModel()).orElse(null);
                    String targetTable = targetModel != null ? targetModel.tableName() : rel.targetModel().toLowerCase(Locale.ROOT);

                    List<String> srcCols = rel.foreignKeyFields().stream()
                            .map(f -> model.findField(f).map(FieldMetadata::dbColumnName).orElse(f))
                            .toList();

                    List<String> tgtCols = rel.referenceFields().stream()
                            .map(f -> targetModel != null
                                    ? targetModel.findField(f).map(FieldMetadata::dbColumnName).orElse(f)
                                    : f)
                            .toList();

                    String fkName = "fk_" + model.tableName() + "_" + String.join("_", srcCols);

                    ForeignKeyDefinition fk = ForeignKeyDefinition.builder(fkName)
                            .sourceTable(model.tableName())
                            .sourceColumns(srcCols)
                            .targetTable(targetTable)
                            .targetColumns(tgtCols)
                            .onDelete(rel.onDelete())
                            .onUpdate(rel.onUpdate())
                            .build();

                    tableBuilder.addForeignKey(fk);
                }
            }

            tables.put(model.tableName(), tableBuilder.build());
        }

        return new DatabaseSchema("public", tables, enums);
    }

    /**
     * Introspects an active PostgreSQL database catalog using the supplied JDBC connection.
     *
     * @param conn       the JDBC connection
     * @param schemaName the schema name (e.g. "public")
     * @return the introspected DatabaseSchema
     * @throws SQLException if introspection fails
     */
    public static DatabaseSchema fromCatalog(Connection conn, String schemaName) throws SQLException {
        return PostgresCatalogIntrospector.introspect(conn, schemaName);
    }

    private static String mapFieldTypeToSql(FieldMetadata field, ModelRegistry registry) {
        // 1. Check if type matches a registered enum
        if (field.typeName() != null && registry.containsEnum(field.typeName())) {
            return field.typeName();
        }

        // 2. Check Java class
        Class<?> jt = field.javaType();
        if (jt != null) {
            if (jt == Integer.class || jt == int.class) return "INTEGER";
            if (jt == Long.class || jt == long.class) return "BIGINT";
            if (jt == Short.class || jt == short.class) return "SMALLINT";
            if (jt == Double.class || jt == double.class) return "DOUBLE PRECISION";
            if (jt == Float.class || jt == float.class) return "REAL";
            if (jt == Boolean.class || jt == boolean.class) return "BOOLEAN";
            if (jt == String.class) return "VARCHAR(255)";
            if (jt == java.math.BigDecimal.class) return "NUMERIC";
            if (jt == java.util.UUID.class) return "UUID";
            if (jt == java.time.LocalDate.class) return "DATE";
            if (jt == java.time.LocalTime.class) return "TIME";
            if (jt == java.time.LocalDateTime.class || jt == java.time.Instant.class || jt == java.time.OffsetDateTime.class) return "TIMESTAMPTZ";
            if (jt == byte[].class) return "BYTEA";
        }

        // 3. Fallback on typeName string
        String tn = field.typeName();
        if (tn != null) {
            return switch (tn.toLowerCase(Locale.ROOT)) {
                case "int", "integer" -> "INTEGER";
                case "bigint", "long" -> "BIGINT";
                case "smallint", "short" -> "SMALLINT";
                case "float", "double" -> "DOUBLE PRECISION";
                case "real" -> "REAL";
                case "decimal", "numeric" -> "NUMERIC";
                case "boolean", "bool" -> "BOOLEAN";
                case "string", "varchar" -> "VARCHAR(255)";
                case "text" -> "TEXT";
                case "datetime", "timestamp" -> "TIMESTAMPTZ";
                case "date" -> "DATE";
                case "time" -> "TIME";
                case "uuid" -> "UUID";
                case "json", "jsonb" -> "JSONB";
                case "bytes", "bytea" -> "BYTEA";
                default -> tn;
            };
        }

        return "VARCHAR(255)";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String schemaName = "public";
        private final Map<String, TableDefinition> tables = new LinkedHashMap<>();
        private final Map<String, List<String>> enums = new LinkedHashMap<>();

        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder addTable(TableDefinition table) {
            Objects.requireNonNull(table, "Table must not be null");
            this.tables.put(table.name(), table);
            return this;
        }

        public Builder addEnum(String enumName, List<String> values) {
            Objects.requireNonNull(enumName, "Enum name must not be null");
            this.enums.put(enumName, values != null ? List.copyOf(values) : List.of());
            return this;
        }

        public DatabaseSchema build() {
            return new DatabaseSchema(schemaName, tables, enums);
        }
    }
}
