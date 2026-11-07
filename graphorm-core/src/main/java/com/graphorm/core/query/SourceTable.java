package com.graphorm.core.query;

import java.util.Objects;

/**
 * Sealed hierarchy representing the source relation in a SQL query.
 * Permitted implementations are physical database tables, database-backed views,
 * derived subqueries, and raw SQL queries.
 */
public sealed interface SourceTable permits
        SourceTable.PhysicalTable,
        SourceTable.DatabaseView,
        SourceTable.SubquerySource,
        SourceTable.RawSqlSource {

    /**
     * Returns the name or alias identifier of this source relation.
     *
     * @return identifier string
     */
    String identifier();

    /**
     * Indicates whether this source represents a database view.
     *
     * @return true if database view
     */
    default boolean isView() {
        return false;
    }

    /**
     * Indicates whether this source represents a nested subquery.
     *
     * @return true if subquery
     */
    default boolean isSubquery() {
        return false;
    }

    /**
     * Indicates whether this source represents a raw SQL subquery.
     *
     * @return true if raw SQL subquery
     */
    default boolean isRawSql() {
        return false;
    }

    /**
     * Physical database table source.
     *
     * @param tableName the database table name
     */
    record PhysicalTable(String tableName) implements SourceTable {
        public PhysicalTable {
            Objects.requireNonNull(tableName, "Table name must not be null");
            if (tableName.isBlank()) {
                throw new IllegalArgumentException("Table name must not be blank");
            }
        }

        @Override
        public String identifier() {
            return tableName;
        }
    }

    /**
     * Database-backed physical or materialized view source.
     *
     * @param viewName the database view name
     */
    record DatabaseView(String viewName) implements SourceTable {
        public DatabaseView {
            Objects.requireNonNull(viewName, "View name must not be null");
            if (viewName.isBlank()) {
                throw new IllegalArgumentException("View name must not be blank");
            }
        }

        @Override
        public String identifier() {
            return viewName;
        }

        @Override
        public boolean isView() {
            return true;
        }
    }

    /**
     * Derived subquery relation source.
     *
     * @param subquery the inner SELECT query
     * @param alias    the subquery alias
     */
    record SubquerySource(SelectQuery subquery, String alias) implements SourceTable {
        public SubquerySource {
            Objects.requireNonNull(subquery, "Subquery must not be null");
            Objects.requireNonNull(alias, "Subquery alias must not be null");
            if (alias.isBlank()) {
                throw new IllegalArgumentException("Subquery alias must not be blank");
            }
        }

        @Override
        public String identifier() {
            return alias;
        }

        @Override
        public boolean isSubquery() {
            return true;
        }
    }

    /**
     * Raw SQL derived subquery relation source (e.g. from @@query).
     *
     * @param sql   the raw SQL query string
     * @param alias the subquery alias
     */
    record RawSqlSource(String sql, String alias) implements SourceTable {
        public RawSqlSource {
            Objects.requireNonNull(sql, "SQL must not be null");
            Objects.requireNonNull(alias, "Subquery alias must not be null");
            if (sql.isBlank()) {
                throw new IllegalArgumentException("SQL must not be blank");
            }
            if (alias.isBlank()) {
                throw new IllegalArgumentException("Subquery alias must not be blank");
            }
        }

        @Override
        public String identifier() {
            return alias;
        }

        @Override
        public boolean isRawSql() {
            return true;
        }
    }

    /**
     * Creates a physical table source.
     *
     * @param tableName table name
     * @return a PhysicalTable instance
     */
    static PhysicalTable table(String tableName) {
        return new PhysicalTable(tableName);
    }

    /**
     * Creates a database view source.
     *
     * @param viewName view name
     * @return a DatabaseView instance
     */
    static DatabaseView view(String viewName) {
        return new DatabaseView(viewName);
    }

    /**
     * Creates a derived subquery source.
     *
     * @param subquery inner SELECT query
     * @param alias    subquery alias
     * @return a SubquerySource instance
     */
    static SubquerySource subquery(SelectQuery subquery, String alias) {
        return new SubquerySource(subquery, alias);
    }

    /**
     * Creates a raw SQL derived subquery source.
     *
     * @param sql   raw SQL query string
     * @param alias subquery alias
     * @return a RawSqlSource instance
     */
    static RawSqlSource rawSql(String sql, String alias) {
        return new RawSqlSource(sql, alias);
    }
}
