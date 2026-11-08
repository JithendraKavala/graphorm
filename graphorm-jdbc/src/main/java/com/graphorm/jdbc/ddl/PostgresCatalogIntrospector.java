package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.IndexType;
import com.graphorm.core.metadata.ReferentialAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Introspects an active PostgreSQL database catalog and builds a {@link DatabaseSchema}.
 */
public final class PostgresCatalogIntrospector {

    private static final Logger log = LoggerFactory.getLogger(PostgresCatalogIntrospector.class);

    private PostgresCatalogIntrospector() {}

    public static DatabaseSchema introspect(Connection conn, String schemaName) throws SQLException {
        Objects.requireNonNull(conn, "Connection must not be null");
        String schema = (schemaName != null && !schemaName.isBlank()) ? schemaName : "public";

        Map<String, List<String>> enums = introspectEnums(conn, schema);
        Map<String, TableDefinition.Builder> tableBuilders = introspectTables(conn, schema);

        introspectColumns(conn, schema, tableBuilders, enums);
        introspectPrimaryKeys(conn, schema, tableBuilders);
        introspectUniqueConstraints(conn, schema, tableBuilders);
        introspectForeignKeys(conn, schema, tableBuilders);
        introspectCheckConstraints(conn, schema, tableBuilders);
        introspectIndexes(conn, schema, tableBuilders);

        Map<String, TableDefinition> tables = new LinkedHashMap<>();
        for (Map.Entry<String, TableDefinition.Builder> entry : tableBuilders.entrySet()) {
            tables.put(entry.getKey(), entry.getValue().build());
        }

        return new DatabaseSchema(schema, tables, enums);
    }

    private static Map<String, List<String>> introspectEnums(Connection conn, String schema) throws SQLException {
        Map<String, List<String>> enums = new LinkedHashMap<>();
        String sql = """
                SELECT t.typname AS enum_name, e.enumlabel AS enum_value
                FROM pg_type t
                JOIN pg_enum e ON t.oid = e.enumtypid
                JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname = ?
                ORDER BY t.typname, e.enumsortorder
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("enum_name");
                    String val = rs.getString("enum_value");
                    enums.computeIfAbsent(name, k -> new ArrayList<>()).add(val);
                }
            }
        }
        return enums;
    }

    private static Map<String, TableDefinition.Builder> introspectTables(Connection conn, String schema) throws SQLException {
        Map<String, TableDefinition.Builder> map = new LinkedHashMap<>();
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_type = 'BASE TABLE'
                  AND table_name NOT IN ('spatial_ref_sys', '_graphorm_migrations')
                ORDER BY table_name
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("table_name");
                    map.put(name, TableDefinition.builder(name).schemaName(schema));
                }
            }
        }
        return map;
    }

    private static void introspectColumns(Connection conn, String schema,
                                         Map<String, TableDefinition.Builder> tableBuilders,
                                         Map<String, List<String>> enums) throws SQLException {
        String sql = """
                SELECT table_name, column_name, data_type, udt_name, character_maximum_length,
                       is_nullable, column_default
                FROM information_schema.columns
                WHERE table_schema = ?
                ORDER BY table_name, ordinal_position
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableDefinition.Builder table = tableBuilders.get(tableName);
                    if (table == null) {
                        continue;
                    }

                    String colName = rs.getString("column_name");
                    String dataType = rs.getString("data_type");
                    String udtName = rs.getString("udt_name");
                    Integer maxLength = rs.getObject("character_maximum_length", Integer.class);
                    boolean isNullable = "YES".equalsIgnoreCase(rs.getString("is_nullable"));
                    String colDefault = rs.getString("column_default");

                    boolean isGenerated = colDefault != null && colDefault.contains("nextval(");
                    String sqlType = normalizeSqlType(dataType, udtName, maxLength, enums);

                    String cleanDefault = null;
                    if (colDefault != null && !isGenerated) {
                        cleanDefault = cleanDefaultValue(colDefault);
                    }

                    ColumnDefinition col = ColumnDefinition.builder(colName)
                            .type(sqlType)
                            .nullable(isNullable)
                            .generated(isGenerated)
                            .defaultValue(cleanDefault)
                            .build();

                    table.addColumn(col);
                }
            }
        }
    }

    private static String normalizeSqlType(String dataType, String udtName, Integer maxLength, Map<String, List<String>> enums) {
        if (enums.containsKey(udtName)) {
            return udtName;
        }

        String lower = dataType.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "integer" -> "INTEGER";
            case "bigint" -> "BIGINT";
            case "smallint" -> "SMALLINT";
            case "boolean" -> "BOOLEAN";
            case "text" -> "TEXT";
            case "character varying" -> maxLength != null ? "VARCHAR(" + maxLength + ")" : "VARCHAR(255)";
            case "timestamp with time zone" -> "TIMESTAMPTZ";
            case "timestamp without time zone" -> "TIMESTAMP";
            case "date" -> "DATE";
            case "time without time zone" -> "TIME";
            case "numeric" -> "NUMERIC";
            case "double precision" -> "DOUBLE PRECISION";
            case "real" -> "REAL";
            case "uuid" -> "UUID";
            case "json", "jsonb" -> "JSONB";
            case "bytea" -> "BYTEA";
            case "user-defined" -> udtName;
            default -> dataType.toUpperCase(Locale.ROOT);
        };
    }

    private static String cleanDefaultValue(String rawDefault) {
        if (rawDefault == null) return null;
        String val = rawDefault.trim();
        // Remove trailing ::regclass, ::text, ::character varying etc.
        int castIdx = val.indexOf("::");
        if (castIdx > 0 && !val.startsWith("nextval(")) {
            val = val.substring(0, castIdx).trim();
        }
        if (val.equalsIgnoreCase("now()") || val.equalsIgnoreCase("CURRENT_TIMESTAMP")) {
            return "CURRENT_TIMESTAMP";
        }
        return val;
    }

    private static void introspectPrimaryKeys(Connection conn, String schema,
                                              Map<String, TableDefinition.Builder> tableBuilders) throws SQLException {
        String sql = """
                SELECT tc.table_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY tc.table_name, kcu.ordinal_position
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableDefinition.Builder table = tableBuilders.get(tableName);
                    if (table != null) {
                        table.addPrimaryKeyColumn(rs.getString("column_name"));
                    }
                }
            }
        }
    }

    private static void introspectUniqueConstraints(Connection conn, String schema,
                                                    Map<String, TableDefinition.Builder> tableBuilders) throws SQLException {
        String sql = """
                SELECT tc.constraint_name, tc.table_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position
                """;

        Map<String, Map<String, List<String>>> tableConstraintMap = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String constraintName = rs.getString("constraint_name");
                    String col = rs.getString("column_name");

                    tableConstraintMap.computeIfAbsent(tableName, k -> new LinkedHashMap<>())
                            .computeIfAbsent(constraintName, k -> new ArrayList<>())
                            .add(col);
                }
            }
        }

        for (Map.Entry<String, Map<String, List<String>>> entry : tableConstraintMap.entrySet()) {
            TableDefinition.Builder table = tableBuilders.get(entry.getKey());
            if (table != null) {
                for (List<String> cols : entry.getValue().values()) {
                    table.addUniqueConstraint(cols);
                }
            }
        }
    }

    private static void introspectForeignKeys(Connection conn, String schema,
                                             Map<String, TableDefinition.Builder> tableBuilders) throws SQLException {
        String sql = """
                SELECT
                    tc.constraint_name,
                    kcu.table_name AS source_table,
                    kcu.column_name AS source_column,
                    ccu.table_name AS target_table,
                    ccu.column_name AS target_column,
                    rc.update_rule,
                    rc.delete_rule
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                JOIN information_schema.referential_constraints rc
                  ON tc.constraint_name = rc.constraint_name
                 AND tc.table_schema = rc.constraint_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'FOREIGN KEY'
                ORDER BY tc.constraint_name, kcu.ordinal_position
                """;

        record FkCollector(String name, String sourceTable, List<String> sourceCols,
                           String targetTable, List<String> targetCols,
                           ReferentialAction onDelete, ReferentialAction onUpdate) {}

        Map<String, FkCollector> fkMap = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String name = rs.getString("constraint_name");
                    String srcTable = rs.getString("source_table");
                    String srcCol = rs.getString("source_column");
                    String tgtTable = rs.getString("target_table");
                    String tgtCol = rs.getString("target_column");
                    String delRule = rs.getString("delete_rule");
                    String updRule = rs.getString("update_rule");

                    ReferentialAction onDel = ReferentialAction.from(delRule);
                    ReferentialAction onUpd = ReferentialAction.from(updRule);

                    fkMap.compute(name, (k, existing) -> {
                        if (existing == null) {
                            List<String> sCols = new ArrayList<>();
                            sCols.add(srcCol);
                            List<String> tCols = new ArrayList<>();
                            tCols.add(tgtCol);
                            return new FkCollector(name, srcTable, sCols, tgtTable, tCols, onDel, onUpd);
                        } else {
                            existing.sourceCols().add(srcCol);
                            existing.targetCols().add(tgtCol);
                            return existing;
                        }
                    });
                }
            }
        }

        for (FkCollector fk : fkMap.values()) {
            TableDefinition.Builder table = tableBuilders.get(fk.sourceTable());
            if (table != null) {
                table.addForeignKey(ForeignKeyDefinition.builder(fk.name())
                        .sourceTable(fk.sourceTable())
                        .sourceColumns(fk.sourceCols())
                        .targetTable(fk.targetTable())
                        .targetColumns(fk.targetCols())
                        .onDelete(fk.onDelete())
                        .onUpdate(fk.onUpdate())
                        .build());
            }
        }
    }

    private static void introspectCheckConstraints(Connection conn, String schema,
                                                   Map<String, TableDefinition.Builder> tableBuilders) throws SQLException {
        String sql = """
                SELECT tc.table_name, tc.constraint_name, cc.check_clause
                FROM information_schema.table_constraints tc
                JOIN information_schema.check_constraints cc
                  ON tc.constraint_name = cc.constraint_name
                 AND tc.table_schema = cc.constraint_schema
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'CHECK'
                  AND tc.constraint_name NOT LIKE '%_not_null'
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableDefinition.Builder table = tableBuilders.get(tableName);
                    if (table != null) {
                        String name = rs.getString("constraint_name");
                        String clause = rs.getString("check_clause");
                        table.addCheckConstraint(new CheckConstraintMetadata(name, clause));
                    }
                }
            }
        }
    }

    private static void introspectIndexes(Connection conn, String schema,
                                          Map<String, TableDefinition.Builder> tableBuilders) throws SQLException {
        String sql = """
                SELECT
                    t.relname AS table_name,
                    i.relname AS index_name,
                    a.attname AS column_name,
                    ix.indisunique AS is_unique,
                    am.amname AS index_type
                FROM pg_index ix
                JOIN pg_class t ON t.oid = ix.indrelid
                JOIN pg_class i ON i.oid = ix.indexrelid
                JOIN pg_am am ON am.oid = i.relam
                JOIN pg_namespace n ON n.oid = t.relnamespace
                JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(ix.indkey)
                WHERE n.nspname = ?
                  AND NOT ix.indisprimary
                  AND i.relname NOT IN (SELECT constraint_name FROM information_schema.table_constraints WHERE table_schema = ?)
                ORDER BY t.relname, i.relname, a.attnum
                """;

        record IdxCollector(String name, String tableName, List<String> fields, boolean isUnique, IndexType type) {}
        Map<String, IdxCollector> indexMap = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String indexName = rs.getString("index_name");
                    String col = rs.getString("column_name");
                    boolean isUnique = rs.getBoolean("is_unique");
                    String typeStr = rs.getString("index_type");
                    IndexType type = IndexType.from(typeStr);

                    indexMap.compute(indexName, (k, existing) -> {
                        if (existing == null) {
                            List<String> fields = new ArrayList<>();
                            fields.add(col);
                            return new IdxCollector(indexName, tableName, fields, isUnique, type);
                        } else {
                            existing.fields().add(col);
                            return existing;
                        }
                    });
                }
            }
        }

        for (IdxCollector idx : indexMap.values()) {
            TableDefinition.Builder table = tableBuilders.get(idx.tableName());
            if (table != null) {
                table.addIndex(IndexMetadata.builder(idx.name())
                        .fields(idx.fields())
                        .unique(idx.isUnique())
                        .type(idx.type())
                        .build());
            }
        }
    }
}
