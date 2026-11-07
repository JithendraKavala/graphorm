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
        List<TableInfo> tables = introspectTables();
        return SchemaEmitter.emit(tables, "postgresql");
    }

    /**
     * Queries database catalog metadata and populates {@link TableInfo} objects.
     */
    public List<TableInfo> introspectTables() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            Map<String, TableInfo> tableMap = queryTablesAndViews(conn);
            queryColumns(conn, tableMap);
            queryPrimaryKeys(conn, tableMap);
            queryUniqueConstraints(conn, tableMap);
            queryForeignKeys(conn, tableMap);

            return new ArrayList<>(tableMap.values());
        }
    }

    private Map<String, TableInfo> queryTablesAndViews(Connection conn) throws SQLException {
        Map<String, TableInfo> map = new LinkedHashMap<>();
        String sql = """
                SELECT table_name, table_type
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name NOT IN ('spatial_ref_sys', 'flyway_schema_history')
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
                SELECT table_name, column_name, data_type, udt_name, is_nullable, column_default, ordinal_position
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

                        table.addColumn(new ColumnInfo(tableName, colName, dataType, udtName, isNullable, colDefault, ordinal));
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
                SELECT tc.table_name, kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
                WHERE tc.table_schema = ?
                  AND tc.constraint_type = 'UNIQUE'
                ORDER BY tc.table_name, kcu.ordinal_position
                """;

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tableName = rs.getString("table_name");
                    TableInfo table = tableMap.get(tableName);
                    if (table != null) {
                        table.addUniqueColumn(rs.getString("column_name"));
                    }
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
                    tc.constraint_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema = kcu.table_schema
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

                        table.addForeignKey(new ForeignKeyInfo(constraint, sourceTable, sourceCol, targetTable, targetCol));
                    }
                }
            }
        }
    }
}
