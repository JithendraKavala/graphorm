package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.ReferentialAction;
import com.graphorm.core.query.BatchInsertQuery;
import com.graphorm.core.query.BulkUpdateQuery;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.UpsertQuery;
import com.graphorm.jdbc.builder.BatchInsertBuilder;
import com.graphorm.jdbc.builder.DeleteBuilder;
import com.graphorm.jdbc.builder.InsertBuilder;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.builder.UpdateBuilder;
import com.graphorm.jdbc.builder.UpsertBuilder;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;
import com.graphorm.jdbc.executor.BoundSql;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SQLite dialect implementation supporting standard double-quoted identifiers,
 * positional '?' placeholders, LIMIT/OFFSET pagination, integer primary key autoincrement semantics,
 * and seamless in-memory execution compatibility (jdbc:sqlite::memory:).
 */
public class SqliteDialect implements SqlDialect {

    private static final Pattern EXPLICIT_EXPRESSION_PATTERN =
            Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*\\s*\\(.*\\)$");

    private final SqliteErrorTranslator errorTranslator = new SqliteErrorTranslator();

    @Override
    public String quoteIdentifier(String identifier) {
        Objects.requireNonNull(identifier, "Identifier must not be null");
        if (identifier.isBlank()) {
            throw new IllegalArgumentException("Identifier must not be blank");
        }

        if (identifier.equals("*")) {
            return "*";
        }

        if (identifier.contains(";") || identifier.contains("--") || identifier.contains("/*")
                || identifier.contains("*/") || identifier.contains("\0") || identifier.contains("\r")
                || identifier.contains("\n")) {
            throw new IllegalArgumentException("Identifier contains illegal syntax characters: " + identifier);
        }

        if (EXPLICIT_EXPRESSION_PATTERN.matcher(identifier).matches()) {
            return identifier;
        }

        if (identifier.contains("(") || identifier.contains(")")) {
            throw new IllegalArgumentException("Identifier contains illegal syntax characters (parentheses): " + identifier);
        }

        // Handle composite or qualified identifiers like "students.id" or "s.*"
        if (identifier.contains(".")) {
            String[] parts = identifier.split("\\.");
            return Arrays.stream(parts)
                    .map(this::quoteSingleIdentifier)
                    .collect(Collectors.joining("."));
        }

        return quoteSingleIdentifier(identifier);
    }

    private String quoteSingleIdentifier(String part) {
        if (part.equals("*")) {
            return "*";
        }
        if (part.startsWith("\"") && part.endsWith("\"") && part.length() >= 2) {
            String inner = part.substring(1, part.length() - 1);
            boolean escaped = true;
            for (int i = 0; i < inner.length(); i++) {
                if (inner.charAt(i) == '"') {
                    if (i + 1 < inner.length() && inner.charAt(i + 1) == '"') {
                        i++;
                    } else {
                        escaped = false;
                        break;
                    }
                }
            }
            if (escaped) {
                return part;
            }
        }
        return "\"" + part.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String formatLimitOffset(Integer limit, Integer offset) {
        if (limit != null && offset != null) {
            return "LIMIT ? OFFSET ?";
        } else if (limit != null) {
            return "LIMIT ?";
        } else if (offset != null) {
            return "LIMIT -1 OFFSET ?";
        }
        return "";
    }

    @Override
    public BoundSql buildSelectSql(SelectQuery query) {
        return new SelectBuilder(this).build(query);
    }

    @Override
    public BoundSql buildInsertSql(InsertQuery query) {
        return new InsertBuilder(this).build(query);
    }

    @Override
    public BoundSql buildUpsertSql(UpsertQuery query) {
        return new UpsertBuilder(this).build(query);
    }

    @Override
    public BoundSql buildBatchInsertSql(BatchInsertQuery query) {
        return new BatchInsertBuilder(this).build(query);
    }

    @Override
    public BoundSql buildUpdateSql(UpdateQuery query) {
        return new UpdateBuilder(this).build(query);
    }

    @Override
    public BoundSql buildBulkUpdateSql(BulkUpdateQuery query) {
        return new UpdateBuilder(this).table(query.table()).values(query.values()).where(query.where()).build();
    }

    @Override
    public BoundSql buildDeleteSql(DeleteQuery query) {
        return new DeleteBuilder(this).build(query);
    }

    @Override
    public String buildExplainSql(String sql) {
        return "EXPLAIN QUERY PLAN " + sql;
    }

    @Override
    public String buildAggregateSql(AggregateType type, String expression) {
        Objects.requireNonNull(type, "AggregateType must not be null");
        Objects.requireNonNull(expression, "Expression must not be null");
        return switch (type) {
            case COUNT -> "COUNT(" + expression + ")";
            case SUM -> "SUM(" + expression + ")";
            case AVG -> "AVG(" + expression + ")";
            case MIN -> "MIN(" + expression + ")";
            case MAX -> "MAX(" + expression + ")";
        };
    }

    @Override
    public boolean supportsReturning() {
        return false;
    }

    @Override
    public boolean supportsNullsOrdering() {
        return true;
    }

    @Override
    public String renderReturningClause(List<String> columns) {
        return "";
    }

    @Override
    public Object convertToDatabaseValue(Object value, FieldMetadata field) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b ? 1 : 0;
        }
        if (value instanceof Enum<?> e) {
            return e.name();
        }
        if (value instanceof UUID u) {
            return u.toString();
        }
        if (value instanceof LocalDate ld) {
            return ld.toString();
        }
        if (value instanceof LocalTime lt) {
            return lt.toString();
        }
        if (value instanceof LocalDateTime ldt) {
            return ldt.toString();
        }
        if (value instanceof Instant inst) {
            return inst.toString();
        }
        if (value instanceof OffsetDateTime odt) {
            return odt.toString();
        }
        if (value instanceof java.util.Date d && !(value instanceof Date) && !(value instanceof Timestamp)) {
            return new Timestamp(d.getTime()).toString();
        }
        return value;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object convertFromDatabaseValue(Object dbValue, FieldMetadata field) {
        if (dbValue == null || field == null || field.javaType() == null) {
            return dbValue;
        }

        Class<?> target = field.javaType();
        if (target.isInstance(dbValue)) {
            return dbValue;
        }

        if (target == String.class) {
            return dbValue.toString();
        }
        if (target == Integer.class || target == int.class) {
            return ((Number) dbValue).intValue();
        }
        if (target == Long.class || target == long.class) {
            return ((Number) dbValue).longValue();
        }
        if (target == Double.class || target == double.class) {
            return ((Number) dbValue).doubleValue();
        }
        if (target == Float.class || target == float.class) {
            return ((Number) dbValue).floatValue();
        }
        if (target == Short.class || target == short.class) {
            return ((Number) dbValue).shortValue();
        }
        if (target == Byte.class || target == byte.class) {
            return ((Number) dbValue).byteValue();
        }
        if (target == BigDecimal.class) {
            if (dbValue instanceof BigDecimal bd) return bd;
            if (dbValue instanceof Long l) return BigDecimal.valueOf(l);
            if (dbValue instanceof Integer i) return BigDecimal.valueOf(i);
            return new BigDecimal(dbValue.toString());
        }
        if (target == BigInteger.class) {
            if (dbValue instanceof BigInteger bi) return bi;
            if (dbValue instanceof Long l) return BigInteger.valueOf(l);
            if (dbValue instanceof Integer i) return BigInteger.valueOf(i);
            if (dbValue instanceof BigDecimal bd) return bd.toBigInteger();
            return new BigInteger(dbValue.toString());
        }
        if (target == Boolean.class || target == boolean.class) {
            if (dbValue instanceof Boolean b) return b;
            if (dbValue instanceof Number n) return n.intValue() != 0;
            String str = dbValue.toString().trim();
            return "1".equals(str) || "true".equalsIgnoreCase(str);
        }
        if (target == UUID.class) {
            if (dbValue instanceof UUID u) return u;
            return UUID.fromString(dbValue.toString());
        }
        if (target == LocalDate.class) {
            if (dbValue instanceof Date d) return d.toLocalDate();
            if (dbValue instanceof Timestamp ts) return ts.toLocalDateTime().toLocalDate();
            return LocalDate.parse(dbValue.toString());
        }
        if (target == LocalTime.class) {
            if (dbValue instanceof Time t) return t.toLocalTime();
            return LocalTime.parse(dbValue.toString());
        }
        if (target == LocalDateTime.class) {
            if (dbValue instanceof Timestamp ts) return ts.toLocalDateTime();
            String str = dbValue.toString();
            if (str.contains(" ")) {
                str = str.replace(' ', 'T');
            }
            return LocalDateTime.parse(str);
        }
        if (target == Instant.class) {
            if (dbValue instanceof Timestamp ts) return ts.toInstant();
            if (dbValue instanceof OffsetDateTime odt) return odt.toInstant();
            return Instant.parse(dbValue.toString());
        }
        if (target == OffsetDateTime.class) {
            if (dbValue instanceof OffsetDateTime odt) return odt;
            if (dbValue instanceof Timestamp ts) return ts.toInstant().atOffset(ZoneOffset.UTC);
            return OffsetDateTime.parse(dbValue.toString());
        }
        if (target.isEnum()) {
            return Enum.valueOf((Class<Enum>) target, dbValue.toString());
        }

        return dbValue;
    }

    @Override
    public SqlErrorTranslator errorTranslator() {
        return errorTranslator;
    }

    // =========================================================================
    // DDL Operations
    // =========================================================================

    @Override
    public String buildCreateTableSql(TableDefinition table) {
        String fullTableName = quoteIdentifier(table.name());
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE TABLE IF NOT EXISTS ").append(fullTableName).append(" (\n");

        List<String> definitions = new ArrayList<>();
        boolean singlePk = table.primaryKeyColumns().size() == 1;

        for (ColumnDefinition col : table.columns()) {
            StringBuilder colSb = new StringBuilder();
            colSb.append("  ").append(quoteIdentifier(col.name())).append(" ");
            if (col.isGenerated()) {
                colSb.append("INTEGER PRIMARY KEY AUTOINCREMENT");
            } else {
                colSb.append(col.type());
                if (!col.isNullable() && !(singlePk && col.isPrimaryKey())) {
                    colSb.append(" NOT NULL");
                }
                if (col.defaultValue() != null && !col.isGenerated() && !col.defaultValue().isBlank()) {
                    colSb.append(" DEFAULT ").append(col.defaultValue());
                }
                if (singlePk && col.isPrimaryKey()) {
                    colSb.append(" PRIMARY KEY");
                } else if (col.isUnique() && !col.isPrimaryKey()) {
                    colSb.append(" UNIQUE");
                }
            }
            definitions.add(colSb.toString());
        }

        // Composite PK
        if (table.primaryKeyColumns().size() > 1) {
            String pkCols = table.primaryKeyColumns().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            String pkName = "pk_" + table.name();
            definitions.add("  CONSTRAINT " + quoteIdentifier(pkName) + " PRIMARY KEY (" + pkCols + ")");
        }

        // Compound unique constraints
        for (List<String> uqCols : table.uniqueConstraints()) {
            String cols = uqCols.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            String uqName = "uq_" + table.name() + "_" + String.join("_", uqCols);
            definitions.add("  CONSTRAINT " + quoteIdentifier(uqName) + " UNIQUE (" + cols + ")");
        }

        // Check constraints
        for (CheckConstraintMetadata chk : table.checkConstraints()) {
            definitions.add("  CONSTRAINT " + quoteIdentifier(chk.name()) + " CHECK (" + chk.expression() + ")");
        }

        sb.append(String.join(",\n", definitions));
        sb.append("\n);");
        return sb.toString();
    }

    @Override
    public String buildDropTableSql(String tableName, boolean ifExists, boolean cascade) {
        return "DROP TABLE " + (ifExists ? "IF EXISTS " : "") + quoteIdentifier(tableName) + ";";
    }

    @Override
    public String buildAddColumnSql(String tableName, ColumnDefinition column) {
        StringBuilder sb = new StringBuilder();
        sb.append("ALTER TABLE ").append(quoteIdentifier(tableName)).append(" ADD COLUMN ");
        sb.append(quoteIdentifier(column.name())).append(" ").append(column.type());
        if (!column.isNullable()) {
            sb.append(" NOT NULL");
        }
        if (column.defaultValue() != null && !column.isGenerated() && !column.defaultValue().isBlank()) {
            sb.append(" DEFAULT ").append(column.defaultValue());
        }
        if (column.isUnique()) {
            sb.append(" UNIQUE");
        }
        sb.append(";");
        return sb.toString();
    }

    @Override
    public String buildDropColumnSql(String tableName, String columnName, boolean ifExists) {
        return "ALTER TABLE " + quoteIdentifier(tableName) + " DROP COLUMN " + quoteIdentifier(columnName) + ";";
    }

    @Override
    public String buildAlterColumnTypeSql(String tableName, String columnName, String newType) {
        return "-- SQLite does not support ALTER COLUMN TYPE";
    }

    @Override
    public String buildAlterColumnNullabilitySql(String tableName, String columnName, boolean nullable) {
        return "-- SQLite does not support ALTER COLUMN NULLABILITY";
    }

    @Override
    public String buildAlterColumnDefaultSql(String tableName, String columnName, String defaultValue) {
        return "-- SQLite does not support ALTER COLUMN DEFAULT";
    }

    @Override
    public String buildDropColumnDefaultSql(String tableName, String columnName) {
        return "-- SQLite does not support ALTER COLUMN DROP DEFAULT";
    }

    @Override
    public String buildCreateIndexSql(IndexMetadata index, String tableName) {
        StringBuilder sb = new StringBuilder();
        sb.append("CREATE ");
        if (index.isUnique()) {
            sb.append("UNIQUE ");
        }
        sb.append("INDEX IF NOT EXISTS ").append(quoteIdentifier(index.name()));
        sb.append(" ON ").append(quoteIdentifier(tableName));
        String cols = index.fields().stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        sb.append(" (").append(cols).append(");");
        return sb.toString();
    }

    @Override
    public String buildDropIndexSql(String indexName, boolean ifExists) {
        return "DROP INDEX " + (ifExists ? "IF EXISTS " : "") + quoteIdentifier(indexName) + ";";
    }

    @Override
    public String buildAddForeignKeySql(ForeignKeyDefinition fk) {
        return "-- SQLite requires foreign keys to be specified during CREATE TABLE";
    }

    @Override
    public String buildDropConstraintSql(String tableName, String constraintName, boolean ifExists) {
        return "-- SQLite does not support ALTER TABLE DROP CONSTRAINT";
    }

    @Override
    public String buildAddUniqueConstraintSql(String tableName, String constraintName, List<String> columns) {
        String cols = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        return "CREATE UNIQUE INDEX IF NOT EXISTS " + quoteIdentifier(constraintName)
                + " ON " + quoteIdentifier(tableName) + " (" + cols + ");";
    }

    @Override
    public String buildAddCheckConstraintSql(String tableName, CheckConstraintMetadata check) {
        return "-- SQLite does not support adding check constraints to existing tables";
    }

    @Override
    public String buildCreateEnumSql(String enumName, List<String> values) {
        return "-- SQLite does not support enum types";
    }

    @Override
    public String buildAddEnumValueSql(String enumName, String value) {
        return "-- SQLite does not support enum types";
    }

    @Override
    public String buildDropEnumSql(String enumName, boolean ifExists) {
        return "-- SQLite does not support enum types";
    }

    @Override
    public String buildAdvisoryLockSql(long lockKey) {
        return "SELECT 1;";
    }

    @Override
    public String buildAdvisoryUnlockSql(long lockKey) {
        return "SELECT 1;";
    }
}
