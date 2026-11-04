---
name: graphorm-view-models
description: >-
  Guides the design, implementation, and maintenance of Schema-Defined View Models in GraphORM.
  Covers view definitions (database-backed and GraphORM-defined), source dependency resolution,
  unified query planning, SQL generation, aggregations, GraphQL exposure, Java client APIs, and testing.
---

# GraphORM View Models Architecture & Engineering

This skill defines the technical standards, data structures, and implementation guidelines for **Schema-Defined View Models** in GraphORM.

View models are a core architectural requirement, not an optional extension. They combine data from multiple database models into unified, queryable, read-only projections without requiring developers to write handwritten Java DTOs, RowMappers, DAOs, or GraphQL DataFetchers.

For detailed architecture and task specifications, see:
- [VIEW_MODEL_DESIGN.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/VIEW_MODEL_DESIGN.md)
- [PHASE_BREAKDOWN.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [IMPLEMENTATION_ROADMAP.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/IMPLEMENTATION_ROADMAP.md)

---

## 1. Model Categories

GraphORM categorizes schema definitions into three distinct primitives:

1. **Regular Models (`model`)**:
   - Map 1:1 to physical database tables.
   - Support full CRUD operations (`findUnique`, `findMany`, `create`, `update`, `delete`).
   - Define primary keys (`@id`), unique constraints (`@unique`), and indexes (`@@index`).
2. **View Models (`view`)**:
   - Map to read-only projections across one or more models, relationships, and aggregations.
   - Enforce read-only semantics: mutations are never generated and are rejected at runtime.
   - Implemented via two distinct strategies (Database-Backed and GraphORM-Defined).
3. **Composite Types (`type`)**:
   - Structural types (e.g. `Address`, `AuditInfo`, `Money`) that can be embedded in models or views.
   - Do not have independent tables or identity constraints.

---

## 2. View Implementation Strategies

### Strategy A: Database-Backed Views
Used when a physical database view or materialized view already exists in the database (e.g. created via database migrations or legacy schemas).

```prisma
view StudentSummary {
    id             Int      @id
    name           String
    departmentName String   @map("dept_name")
    totalCredits   Int      @map("total_credits")

    @@view
    @@map("vw_student_summary")
}
```
- **Execution**: The query planner treats this as a direct `SELECT` against the database view (`SourceTable.DatabaseView`).
- **Capabilities**: Supports schema introspection, selection-aware projection, pushdown filtering, sorting, pagination, and automatic read-only GraphQL exposure.

### Strategy B: GraphORM-Defined Views
Used when developers want to declare an aggregated or multi-table projection directly in `schema.graphorm` without creating or maintaining a physical database view.

```prisma
view StudentDashboard {
    studentId      Int     @map("s.id")
    studentName    String  @map("s.name")
    departmentName String? @map("d.name")
    courseCount    Int     @aggregate(count: "e.id")
    gpa            Float?  @expression("AVG(e.grade_points)")
    primaryAddress Address?

    @@from(Student, as: "s")
    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
    @@groupBy(["s.id", "s.name", "d.name"])
}
```
- **Execution**: Dynamically translated into parameterized SQL with joins, `GROUP BY`, and aggregate functions.
- **Capabilities**: Zero DDL required. Joins and aggregations are resolved dynamically by the query engine.

---

## 3. Query Engine & Unified Query Planner

View models do **not** use a separate query execution engine. They are executed through the same unified query planner in `graphorm-jdbc` and `graphorm-graphql`.

### Internal Query AST
```java
public record SelectQuery(
    SourceTable source,
    String tableAlias,
    List<ProjectedColumn> columns,
    List<JoinClause> joins,
    WherePredicate where,
    List<GroupByClause> groupBy,
    HavingPredicate having,
    List<OrderByClause> orderBy,
    Integer limit,
    Integer offset
) implements SqlQuery {}
```

### Planning & Optimization Pipeline

1. **Source Dependency Resolution**:
   - Resolves the root model (`@@from`) and all joined models (`@@leftJoin`, `@@innerJoin`).
   - If a view depends on another view, the planner resolves the dependency graph as nested subqueries or Common Table Expressions (CTEs).
2. **Selective Projection & Join Pruning**:
   - Inspects the GraphQL selection set or client query.
   - If the client requests only `{ studentName }`, joined tables not needed for `WHERE`, `ORDER BY`, or `GROUP BY` can be safely pruned from the SQL query.
3. **Hidden Dependency Column Injection**:
   - If downstream resolvers or nested DataLoader calls require a join key or foreign key not explicitly requested by the user, the planner automatically injects it into the SQL `SELECT` list (`isHiddenDependency = true`).
4. **Pushdown Filtering, Sorting, and Pagination**:
   - Filters are qualified by table alias (e.g. `s.name = ?`, `d.id = ?`) and placed in `WHERE`.
   - Aggregation filters are placed in `HAVING`.
   - `LIMIT ? OFFSET ?` are appended to the query so pagination occurs inside PostgreSQL.
5. **No Whole-Table In-Memory Loads**:
   - All multi-table correlation and aggregation occurs within the database engine. Whole tables are never pulled into JVM memory.
6. **N+1 Prevention with DataLoader**:
   - If a view model references a nested collection or regular model relation, keys are batched using GraphQL Java `DataLoader`.

---

## 4. GraphQL Integration

View models are automatically mapped to read-only GraphQL types and queries during startup:

1. **GraphQL Type Generation**:
   - Maps each field in `view` to a field in GraphQL SDL (`type StudentDashboard { ... }`).
2. **Read-Only Root Queries**:
   - `Query.<view>s(where: ..., orderBy: ..., limit: ..., offset: ...): [<View>!]!`
   - `Query.<view>(<idArgs>): <View>`
3. **Strict Mutation Exclusion**:
   - **Never** register `Mutation.create<View>`, `Mutation.update<View>`, or `Mutation.delete<View>`.
   - Schema validation fails if mutations are bound to view models without an explicit custom resolver.
4. **Coexistence**:
   - Custom `@QueryMapping` controllers take precedence over auto-registered fetchers.

---

## 5. Java Client API

View models are exposed via the generated fluent client (`GraphOrmClient`) with compile-time read-only enforcement:

```java
GraphOrmClient db = ...;

// findMany with filtering and pagination
List<StudentDashboard> dashboards = db.studentDashboard().findMany(q -> q
    .where(d -> d.courseCount().gte(3))
    .orderBy(d -> d.studentName().asc())
    .limit(25)
);

// findUnique
Optional<StudentDashboard> dashboard = db.studentDashboard().findUnique(q -> q
    .where(d -> d.studentId().eq(1001))
);

// COMPILE ERROR: db.studentDashboard().create(...) does not exist!
```

---

## 6. Incremental Implementation Stages

To ensure modularity and stability, view models must be implemented in the following 6 stages:

1. **Stage 1: Database-Backed View Introspection & Querying** (Phase 7):
   - Support `@@view` and `@@map` in `graphorm-schema`.
   - Direct SELECT execution via `SourceTable.DatabaseView`.
   - Automatic read-only GraphQL query registration.
2. **Stage 2: Basic GraphORM-Defined Views with Joins** (Phase 8):
   - Support `@@from`, `@@leftJoin`, and `@@innerJoin`.
   - Translate join declarations into `JoinClause` AST.
   - Dynamic multi-table SQL generation in `PostgreSqlDialect`.
3. **Stage 3: Pushdown Filtering, Sorting, and Pagination** (Phase 8):
   - Map view field criteria to qualified source column predicates.
   - Parameterized `WHERE`, `ORDER BY`, `LIMIT`, and `OFFSET` execution.
4. **Stage 4: Aggregations and Computed Fields** (Phase 9):
   - Parse and validate `@aggregate(count | sum | avg | min | max)` and `@expression(...)`.
   - Generate SQL `GROUP BY` and `HAVING` clauses.
5. **Stage 5: Nested Relationships and Reusable Views** (Phase 11):
   - Enable views to reference regular models and reusable views as sources.
   - Auto-inject hidden dependency keys and wire `DataLoader` batching.
6. **Stage 6: Advanced SQL Optimization & Materialized Views** (Phase 15):
   - Generate SQL Common Table Expressions (CTEs) for deeply nested views.
   - Materialized view refresh support (`REFRESH MATERIALIZED VIEW`).

---

## 7. Testing Standards for View Models

1. **AST & Parser Tests**: Test parsing of `view`, `type`, join attributes, and expressions with diagnostic line/column assertions.
2. **Validation Tests**: Verify detection of unknown source models, invalid join columns, cyclic view references, and illegal write defaults.
3. **Dialect SQL Verification**: Verify parameterized SQL generation for joins and aggregations without execution.
4. **Testcontainers PostgreSQL Tests**: Execute view queries against real PostgreSQL database views and dynamic GraphORM joins.
5. **N+1 Batching Tests**: Assert SQL query execution counts when traversing nested relations on view models.
6. **Read-Only Invariant Tests**: Assert that neither generated Java clients nor GraphQL schemas expose mutation endpoints for view models.
