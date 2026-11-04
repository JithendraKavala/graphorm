# GraphORM: Comprehensive Testing Strategy & Quality Engineering

## 1. Quality Objectives & "Test Everything" Mandate

In GraphORM, **testing is a first-class architectural requirement, not an afterthought**. Because GraphORM is an infrastructure-level framework responsible for data integrity, query optimization, and compile-time code generation, **everything must be tested as much as possible**.

### Core Testing Mandates:
- **Maximum Test Coverage**: Target >90% line and branch coverage across core components (`graphorm-core`, `graphorm-schema`, `graphorm-jdbc`, `graphorm-graphql`).
- **Zero Untested Edge Cases**: Every grammar rule, token, AST node, SQL clause, filter operator, type converter, and error diagnostic must have dedicated unit tests.
- **Dialect SQL Precision**: 100% of generated SQL strings, aliases, table qualifications, and parameter bindings must be verified with exact assertions.
- **Real Database Verification**: All database interactions must be verified against real PostgreSQL instances using Testcontainers—never mocked away.
- **N+1 Regression Prevention**: Every relationship and nested view fetcher must assert exact SQL execution counts using query interceptors.
- **Generated Code Compilation**: All code generation templates must be verified by compiling the generated Java source in test suites.

---

## 2. Exhaustive Test Suites by Module

### 2.1 `graphorm-core`
- **Metadata Immutability Tests**: Assert that `ModelMetadata`, `ViewModelMetadata`, `FieldMetadata`, `RelationMetadata`, and `CompositeTypeMetadata` are strictly immutable and defensive against mutations.
- **Query AST Representation Tests**: Verify construction, immutability, and composition of `SelectQuery`, `JoinClause`, `WherePredicate`, `GroupByClause`, and `OrderByClause`.
- **Predicate Tree Tests**: Test all logical and relational operators (`eq`, `neq`, `in`, `gt`, `gte`, `lt`, `lte`, `like`, `isNull`, `isNotNull`, `and`, `or`, `not`) with varied scalar types.
- **Type Conversion Tests**: Verify bidirectional type mapping between Java types, JDBC types, and schema scalar types.
- **Exception Hierarchy Tests**: Ensure all exceptions properly retain root causes, diagnostic messages, and context.

### 2.2 `graphorm-schema`
- **Lexer Edge Cases**: Test valid tokens, invalid symbols, whitespace, comments, multiline blocks, escaped characters, string literals, and numbers.
- **Parser Grammar Coverage**:
  - Regular `model` blocks with `@id`, `@default`, `@unique`, `@map`, `@relation`.
  - View model `view` blocks with `@@view`, `@@map`, `@@from`, `@@leftJoin`, `@@innerJoin`, `@@groupBy`.
  - Composite `type` blocks with nested attributes.
  - `datasource` and `generator` blocks.
- **Diagnostic Error Reporting**: Assert line, column, and error message accuracy for:
  - Missing `@id` on models.
  - Non-existent types or undefined model references.
  - Invalid source models or fields in view joins (`@@from`, `@@leftJoin`).
  - Join predicate type mismatches.
  - Illegal write defaults (`@default(autoincrement())`) on read-only views.
  - Cyclic view dependencies (View A -> View B -> View A).
  - Unmatched relationship fields.

### 2.3 `graphorm-generator`
- **Template Generation Tests**:
  - Model record generation (Java 21 records with correct accessors and nullability).
  - View model record generation (read-only projections).
  - Composite type records.
  - Filter criteria builder generation.
  - Metadata class generation (static constants, no runtime reflection).
  - Input types (`CreateInput`, `UpdateInput`) generated **only** for regular models.
- **Compile-Time Verification**: Test harness compiles generated Java code in-memory using `JavaCompiler` (JSR-199) to assert 100% valid syntax without compilation warnings.
- **Deterministic Output Tests**: Assert that running generation repeatedly against the same schema produces byte-identical output.

### 2.4 `graphorm-jdbc`
- **Dialect Unit Tests (PostgreSQL)**:
  - Table and view identifier quoting (`"users"`, `"user_id"`).
  - Pagination formatting (`LIMIT ? OFFSET ?`).
  - Single-table queries, multi-table joins (`LEFT JOIN`, `INNER JOIN`).
  - Dynamic column projection (selected columns vs pruned columns).
  - Aggregation functions (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`) and `GROUP BY` / `HAVING`.
  - Subquery and CTE generation for views.
  - Bound parameter extraction and positional ordering (`BoundSql`).
- **Testcontainers PostgreSQL Integration Tests**:
  - Spin up isolated `postgres:16-alpine` container.
  - Execute DDL schemas and verify execution against real PostgreSQL.
  - CRUD operations on regular models (`create`, `findMany`, `findUnique`, `update`, `delete`).
  - Direct SELECT queries on database-backed views (`@@view`).
  - Dynamic multi-table queries on GraphORM-defined views.
  - Filter pushdown, ordering pushdown, and pagination pushdown.
  - Transaction rollbacks, commits, and isolation levels.
  - Connection leak detection.

### 2.5 `graphorm-graphql`
- **Schema Mapping Tests**: Assert automatic binding of GraphQL SDL types to GraphORM models and views.
- **DataFetcher Registration Tests**:
  - Query fetchers registered for models and views.
  - Mutation fetchers registered **exclusively** for models (`create`, `update`, `delete`).
  - Assert that NO mutations are registered for view models.
- **Selection-Set Projection Tests**:
  - Verify that only requested GraphQL fields are included in the SQL `SELECT` projection.
  - Verify that hidden dependency columns (foreign keys, join identifiers) are automatically injected.
- **N+1 Prevention & DataLoader Tests**:
  - Use SQL statement tracker to assert exact query counts:
    - 1 query for primary view/model list.
    - 1 batched query for each traversed relationship (`WHERE id IN (?, ?, ...)`).
  - Zero N+1 query regression guarantee.
- **Argument Binding Tests**:
  - Verify translation of GraphQL filter arguments to SQL `WHERE` / `HAVING` predicates.
  - Validate pagination arguments (`first`, `after`, `limit`, `offset`).

### 2.6 `graphorm-spring-boot-starter`
- **Auto-Configuration Tests**:
  - Use `ApplicationContextRunner` to verify conditional bean creation (`@ConditionalOnClass`, `@ConditionalOnMissingBean`).
  - Test scenario with valid `schema.graphorm` and active `DataSource`.
  - Test graceful fallback when GraphORM is disabled via configuration (`graphorm.enabled=false`).
  - Verify integration with Spring's `PlatformTransactionManager` via `@Transactional`.
  - Verify registration of `GraphQlSourceBuilderCustomizer`.

### 2.7 `graphorm-cli`
- **CLI Command Tests**:
  - Test `graphorm validate <schema-path>` with valid and invalid schemas.
  - Test `graphorm generate <schema-path> --output <dir>` verifying generated files.
  - Assert non-zero exit codes on validation errors.

---

## 3. Testing Hierarchy & Tooling

| Level | Scope | Tech Stack | Execution Rule |
|---|---|---|---|
| **Unit Tests** | AST, Lexer, Parser, Generators, Dialects | JUnit 5, AssertJ | Fast, zero external dependencies, run on every commit |
| **Compiler Tests** | Generated Java Sources | `javax.tools.JavaCompiler` | Validate generated records compile cleanly |
| **Integration Tests** | Real Database Queries, Views, Transactions | JUnit 5, Testcontainers PostgreSQL | Run with Docker on build verification |
| **GraphQL E2E Tests** | Selection Sets, Batch Loaders, DataFetchers | Spring for GraphQL Test / `GraphQlTester` | Verify end-to-end query execution & query counts |

---

## 4. Quality Gates & CI Enforcement

- **Pre-Commit / Pre-Push**: All unit and compiler tests must pass (`mvn clean test`).
- **Pull Request Quality Gate**:
  - 100% test pass rate.
  - No new feature accepted without corresponding unit + integration tests.
  - No bug fix accepted without a regression test reproducing the issue.
  - Code coverage thresholds strictly enforced via JaCoCo.
