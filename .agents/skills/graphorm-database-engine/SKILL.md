---
name: graphorm-database-engine
description: >-
  Guides JDBC integration, SQL generation, result mapping, database dialects, transactions,
  and safe query execution for both regular models and view models. Use when implementing or modifying
  graphorm-jdbc, SQL dialects, join/aggregation generation, or connection/transaction handling.
---

# GraphORM Database Engine & JDBC

This skill guides the design and implementation of the database execution layer in `graphorm-jdbc`.

For implementation roadmap and phase specifications, see:
- [PHASE_BREAKDOWN.md - Phases 4, 5, 6, 7, 8, 9](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [QUERY_ENGINE_DESIGN.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/QUERY_ENGINE_DESIGN.md)
- [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)

---

## Core Principles

1. **Zero Concatenation**: NEVER construct SQL strings by interpolating untrusted parameters. All user values must be bound using JDBC `PreparedStatement` parameters (`?`).
2. **Dialect Abstraction**: All database-specific SQL generation (identifier quoting, pagination, UPSERT, autoincrement retrieval, joins, and aggregations) must implement `SqlDialect`.
3. **Unified Query Execution**: Regular models and view models share the same execution pipeline and query AST.
4. **Strict Read-Only Enforcement on Views**: Attempting to execute `buildInsertSql`, `buildUpdateSql`, or `buildDeleteSql` against a view model metadata descriptor must immediately throw a `ReadOnlyViewException`.
5. **Selection-Aware Column Projection**: Fetch only requested columns + required foreign key and join dependency columns.
6. **Pushdown Everything**: Filter conditions (`WHERE`), ordering (`ORDER BY`), pagination (`LIMIT/OFFSET`), and grouping (`GROUP BY`) must be executed directly in the database, avoiding pulling full tables into Java.

## Dialect System

```java
public interface SqlDialect {
    String quoteIdentifier(String identifier);
    String formatLimitOffset(int limit, int offset);
    BoundSql buildSelectSql(SelectQuery query);
    BoundSql buildInsertSql(InsertQuery query);
    BoundSql buildUpdateSql(UpdateQuery query);
    BoundSql buildDeleteSql(DeleteQuery query);
    String buildAggregateSql(AggregateType type, String expression);
    Object convertToDatabaseValue(Object value, FieldMetadata field);
    Object convertFromDatabaseValue(Object dbValue, FieldMetadata field);
}
```

- Initial target: `PostgreSqlDialect`.
- Future targets: `MySqlDialect`, `H2Dialect`.

## Unified Query Models in `graphorm-core`

- `SqlQuery`: Root interface for structured query representations.
- `SelectQuery`: Supports physical tables, database views, and subqueries/CTEs:
  - `SourceTable`: `PhysicalTable`, `DatabaseView`, `SubquerySource`.
  - `List<ProjectedColumn>`: direct columns, computed expressions, aggregate functions.
  - `List<JoinClause>`: `INNER`, `LEFT`, `RIGHT` joins with typed `JoinCondition`.
  - `WherePredicate`: Composable filter tree (`Eq`, `In`, `And`, `Or`, `Gt`, `Lt`, `Like`, `IsNull`).
  - `List<GroupByClause>`: column expressions for view aggregations.
  - `HavingPredicate`: post-aggregation filters.
  - `List<OrderByClause>`: sorting.
  - `Integer limit`, `Integer offset`: pagination.
- `BoundSql`: Immutable pair of `String sql` and `List<Object> parameters`.

## Safe Execution Workflow

```text
SelectQuery (Model or View) -> SqlDialect.buildSelectSql() -> BoundSql
-> DataSource.getConnection() -> Connection.prepareStatement(sql)
-> Bind Parameters (with TypeConverter) -> executeQuery()
-> DynamicRowMapper.mapRow() -> Result Object Record / Projection
```

## View Query Execution Strategies

1. **Database-Backed Views**:
   - Executed as direct `SELECT` against the physical view or materialized view (`FROM "vw_student_summary"`).
2. **GraphORM-Defined Virtual Views**:
   - Translated into parameterized multi-table `SELECT` statements with `JOIN` and optional `GROUP BY` clauses.
   - Dialect ensures proper table/alias qualification (e.g. `"s"."id"`, `"d"."name"`).
   - Dynamic projection ensures unselected join branches or aggregation expressions are pruned when omitted from the client query.
