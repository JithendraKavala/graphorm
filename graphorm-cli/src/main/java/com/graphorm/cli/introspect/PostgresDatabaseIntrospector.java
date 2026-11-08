package com.graphorm.cli.introspect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Introspects a PostgreSQL database catalog and generates a GraphORM schema DSL.
 */
public final class PostgresDatabaseIntrospector {

    private static final Logger log = LoggerFactory.getLogger(PostgresDatabaseIntrospector.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schemaName;

    public PostgresDatabaseIntrospector(String jdbcUrl, String username, String password, String schemaName) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.username = username != null ? username : "postgres";
        this.password = password != null ? password : "";
        this.schemaName = (schemaName != null && !schemaName.isBlank()) ? schemaName : "public";
    }

    /**
     * Introspects the database and produces a GraphORM schema DSL string.
     *
     * @return generated schema DSL string
     * @throws SQLException if a database access error occurs
     */
    public String introspect() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            List<EnumInfo> enums = queryEnums(conn);
            List<TableInfo> tables = introspectTables(conn);
            return SchemaEmitter.emit(tables, enums, "postgresql");
        }
    }

    /**
     * Queries database catalog metadata and populates {@link TableInfo} objects.
     */
    public List<TableInfo> introspectTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            return introspectTables(conn);
        }
    }

    /**
     * Queries database catalog for PostgreSQL enum types.
     */
    public List<EnumInfo> introspectEnums() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            return queryEnums(conn);
        }
    }

    private List<TableInfo> introspectTables(Connection conn) throws SQLException {
        Map<String, TableInfo> tableMap = queryTablesAndViews(conn);
        queryColumns(conn, tableMap);
        queryPrimaryKeys(conn, tableMap);
        queryUniqueConstraints(conn, tableMap);
        queryForeignKeys(conn, tableMap);
        queryIndexes(conn, tableMap);
        queryCheckConstraints(conn, tableMap);

        return new ArrayList<>(tableMap.values());
    }

    private List<EnumInfo> queryEnums(Connection conn) throws SQLException {
        String sql = """
                SELECT t.typname AS enum_name, e.enumlabel AS enum_value
                FROM pg_type t
                JOIN pg_enum e ON t.oid = e.enumtypid
                JOIN pg_namespace n ON n.oid = t.typnamespace
                WHERE n.nspname = ?
                ORDER BY t.typname, e.enumsortorder
                """;

        Map<String, List<String>> enumMap = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String enumName = rs.getString("enum_name");
                    String enumVal = rs.getString("enum_value");
                    enumMap.computeIfAbsent(enumName, k -> new ArrayList<>()).add(enumVal);
                }
            }
        }

        List<EnumInfo> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : enumMap.entrySet()) {
            result.add(new EnumInfo(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    private Map<String, TableInfo> queryTablesAndViews(Connection conn) throws SQLException {
        Map<String, TableInfo> map = new LinkedHashMap<>();
        String sql = """
                SELECT table_name, table_type
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name NOT IN ('spatial_ref_sys', 'flyway_schema_history', '_graphorm_migrations')
                ORDER BY table_name
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String tableType = rs.getString("table_type");
                    boolean isView = "VIEW".equalsIgnoreCase(tableType);
                    map.put(tableName, new TableInfo(tableName, isView));
                }
            }
        }
        return map;
    }

    private void queryColumns(Connection conn, Map<String, TableInfo> tableMap) throws SQLException {
        String sql = """
                SELECT table_name, column_name, data_type, udt_name, is_nullable, column_default, ordinal_position,
                       character_maximum_length, numeric_precision, numeric_scale
                FROM information_schema.columns
                WHERE table_schema = ?
                ORDER BY table_name, ordinal_position
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableInfo table = tableMap.get(tableName);
                    if (table != null) {
                        String colName = rs.getString("column_name");
                        String dataType = rs.getString("data_type");
                        String udtName = rs.getString("udt_name");
                        boolean isNullable = "YES".equalsIgnoreCase(rs.getString("is_nullable"));
                        String colDefault = rs.getString("column_default");
                        int ordinal = rs.getInt("ordinal_position");
                        Integer charMaxLen = rs.getObject("character_maximum_length") != null ? rs.getInt("character_maximum_length") : null;
                        Integer numPrec = rs.getObject("numeric_precision") != null ? rs.getInt("numeric_precision") : null;
                        Integer numScale = rs.getObject("numeric_scale") != null ? rs.getInt("numeric_scale") : null;

                        table.addColumn(new ColumnInfo(tableName, colName, dataType, udtName, isNullable, colDefault, ordinal, charMaxLen, numPrec, numScale));
                    }
                }
            }
        }
    }

    private void queryPrimaryKeys(Connection conn, Map<String, TableInfo> tableMap) throws SQLException {
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
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableInfo table = tableMap.get(tableName);
                    if (table != null) {
                        table.addPrimaryKeyColumn(rs.getString("column_name"));
                    }
                }
            }
        }
    }

    private void queryUniqueConstraints(Connection conn, Map<String, TableInfo> tableMap) throws SQLException {
        String sql = """
                SELECT tc.table_name, tc.constraint_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY tc.table_name, tc.constraint_name, kcu.ordinal_position
                """;

        Map<String, Map<String, List<String>>> tableConstraints = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String constraintName = rs.getString("constraint_name");
                    String colName = rs.getString("column_name");

                    tableConstraints.computeIfAbsent(tableName, k -> new LinkedHashMap<>())
                            .computeIfAbsent(constraintName, k -> new ArrayList<>())
                            .add(colName);
                }
            }
        }

        for (Map.Entry<String, Map<String, List<String>>> tableEntry : tableConstraints.entrySet()) {
            TableInfo table = tableMap.get(tableEntry.getKey());
            if (table != null) {
                for (List<String> cols : tableEntry.getValue().values()) {
                    table.addUniqueConstraint(cols);
                }
            }
        }
    }

    private void queryForeignKeys(Connection conn, Map<String, TableInfo> tableMap) throws SQLException {
        String sql = """
                SELECT
                    kcu.table_name AS source_table,
                    kcu.column_name AS source_column,
                    ccu.table_name AS target_table,
                    ccu.column_name AS target_column,
                    tc.constraint_name,
                    rc.delete_rule,
                    rc.update_rule
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
                ORDER BY kcu.table_name, kcu.ordinal_position
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String sourceTable = rs.getString("source_table");
                    TableInfo table = tableMap.get(sourceTable);
                    if (table != null) {
                        String sourceCol = rs.getString("source_column");
                        String targetTable = rs.getString("target_table");
                        String targetCol = rs.getString("target_column");
                        String constraint = rs.getString("constraint_name");
                        String onDelete = rs.getString("delete_rule");
                        String onUpdate = rs.getString("update_rule");

                        table.addForeignKey(new ForeignKeyInfo(constraint, sourceTable, sourceCol, targetTable, targetCol, onDelete, onUpdate));
                    }
                }
            }
        }
    }

    private void queryIndexes(Connection conn, Map<String, TableInfo> tableMap) throws SQLException {
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

        Map<String, Map<String, IndexCollector>> tableIndexMap = new LinkedHashMap<>();

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    String indexName = rs.getString("index_name");
                    String colName = rs.getString("column_name");
                    boolean isUnique = rs.getBoolean("is_unique");
                    String indexType = rs.getString("index_type");

                    tableIndexMap.computeIfAbsent(tableName, k -> new LinkedHashMap<>())
                            .compute(indexName, (k, existing) -> {
                                if (existing == null) {
                                    List<String> cols = new ArrayList<>();
                                    cols.add(colName);
                                    return new IndexCollector(indexName, cols, isUnique, indexType);
                                } else {
                                    existing.columns.add(colName);
                                    return existing;
                                }
                            });
                }
            }
        }

        for (Map.Entry<String, Map<String, IndexCollector>> entry : tableIndexMap.entrySet()) {
            TableInfo table = tableMap.get(entry.getKey());
            if (table != null) {
                for (IndexCollector ic : entry.getValue().values()) {
                    table.addIndex(new IndexInfo(ic.name, ic.columns, ic.isUnique, ic.indexType));
                }
            }
        }
    }

    private static class IndexCollector {
        final String name;
        final List<String> columns;
        final boolean isUnique;
        final String indexType;

        IndexCollector(String name, List<String> columns, boolean isUnique, String indexType) {
            this.name = name;
            this.columns = columns;
            this.isUnique = isUnique;
            this.indexType = indexType;
        }
    }

    private void queryCheckConstraints(Connection conn, Map<String, TableInfo> tableMap) throws SQLException {
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
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableInfo table = tableMap.get(tableName);
                    if (table != null) {
                        String constraint = rs.getString("constraint_name");
                        String clause = rs.getString("check_clause");
                        table.addCheckConstraint(new CheckConstraintInfo(constraint, clause));
                    }
                }
            }
        }
    }
}
