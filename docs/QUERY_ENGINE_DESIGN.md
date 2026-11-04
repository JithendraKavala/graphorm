# GraphORM: Query Engine & SQL Generation Design

## 1. Overview

The Query Engine in `graphorm-jdbc` converts high-level query specifications into safe, optimized, dialect-specific SQL and executes them against a `DataSource`.

A core architectural principle of GraphORM is that **both regular models and view models share a unified query planner and execution engine**. Rather than introducing a disparate engine for views, the unified planner processes regular tables, database-backed views, and virtual GraphORM-defined views uniformly.

## 2. Unified Query AST Representation

Rather than operating on raw SQL strings or maintaining separate ASTs for views and models, the engine builds a strongly typed, composable AST:

```java
public record SelectQuery(
    SourceTable source,                  // Table name, database view, or subquery/CTE
    String tableAlias,                   // e.g. "s"
    List<ProjectedColumn> columns,       // Direct columns, expressions, aggregations
    List<JoinClause> joins,              // LEFT, INNER, RIGHT joins
    WherePredicate where,                // Type-safe filter criteria tree
    List<GroupByClause> groupBy,         // Group by expressions for aggregations
    HavingPredicate having,              // Post-aggregation filters
    List<OrderByClause> orderBy,         // Sorting criteria
    Integer limit,                       // Pagination limit
    Integer offset                       // Pagination offset
) implements SqlQuery {}

public sealed interface SourceTable permits PhysicalTable, DatabaseView, SubquerySource {
    record PhysicalTable(String tableName) implements SourceTable {}
    record DatabaseView(String viewName, boolean materialized) implements SourceTable {}
    record SubquerySource(SelectQuery subquery, String alias) implements SourceTable {}
}

public record ProjectedColumn(
    String expression,                   // Column name, "s.name", "COUNT(e.id)"
    String alias,                        // Output alias e.g. "courseCount"
    boolean isHiddenDependency           // True if injected solely for downstream relation loading
) {}

public record JoinClause(
    JoinType type,                       // INNER, LEFT, RIGHT
    SourceTable table,
    String alias,
    JoinCondition condition
) {}
```

## 3. Query Planning for View Models

The unified query planner handles view models via two operational pathways:

### 3.1 Database-Backed Views
- Treated as a pre-materialized `SourceTable.DatabaseView`.
- Queries generate standard `SELECT <projected_columns> FROM "vw_student_summary" WHERE ... LIMIT ? OFFSET ?`.
- Inherits all selection-set filtering, pagination, and projection pruning capabilities.

### 3.2 GraphORM-Defined Views
- Transformed dynamically into a structured join and aggregation plan:
  1. **Source Dependency Resolution**: Identifies root source (`@@from`) and all joined sources (`@@leftJoin`, `@@innerJoin`).
  2. **Selective Projection**: Inspects requested fields. Columns not requested by the caller (and not needed in `GROUP BY`, `JOIN`, or `ORDER BY`) are omitted from SQL projection.
  3. **Hidden Dependency Column Injection**: Automatically injects join foreign keys and relation identifiers required for downstream nested resolution, marking them as `isHiddenDependency = true` so they are stripped before delivering the client result.
  4. **Pushdown Filtering & Sorting**: User criteria applied on view fields are mapped to qualified source expressions (e.g., view field `departmentName` -> `d.name = ?`) and pushed down to the SQL `WHERE` clause.
  5. **Pushdown Pagination**: `LIMIT` and `OFFSET` are pushed directly to the database query.
  6. **Zero Memory Table Dumps**: Joins, aggregations, and filters are executed within PostgreSQL. The engine never pulls whole tables into Java memory.

## 4. The `SqlDialect` Interface

Database variations (such as quoting, pagination, aggregation functions, and subquery syntax) are isolated within `SqlDialect`:

```java
public interface SqlDialect {
    String quoteIdentifier(String identifier);
    String formatLimitOffset(int limit, int offset);
    BoundSql buildSelect(SelectQuery query);
    BoundSql buildInsert(InsertQuery query);
    BoundSql buildUpdate(UpdateQuery query);
    BoundSql buildDelete(DeleteQuery query);
    String aggregateFunction(AggregateType type, String expression);
}
```

### PostgreSQL Dialect (`PostgreSqlDialect`)
- Quotes identifiers with double quotes: `"students"`, `"department_id"`.
- Uses positional parameter placeholders: `?`.
- Implements pagination using standard `LIMIT ? OFFSET ?`.
- Supports `RETURNING *` for insert/update operations (regular models only).
- Translates aggregations (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`) into standard SQL.
- Supports Common Table Expressions (CTEs) and subqueries in `FROM` clauses for reusable/nested views.

## 5. Parameterized Execution & Result Mapping

```java
public record BoundSql(String sql, List<Object> parameters) {}
```

Execution flow:
1. GraphQL DataFetcher or Java Client calls `queryPlanner.plan(request)`.
2. Planner generates an optimized `SelectQuery` AST.
3. `dialect.buildSelect(query)` produces a parameterized `BoundSql`.
4. `JdbcExecutor` executes the `PreparedStatement` with bound parameters.
5. `DynamicRowMapper` maps `ResultSet` rows into immutable Java model records or view projection records.
6. Hidden dependency columns are utilized for any downstream `DataLoader` calls and filtered from the final payload.
