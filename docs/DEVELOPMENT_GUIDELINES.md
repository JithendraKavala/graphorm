# GraphORM: Development Guidelines & Engineering Standards

## 1. Code Standards & Conventions

- **Java Version**: Java 21 LTS baseline.
- **Immutability**: Make all metadata, query representations, and generated model/view classes immutable (prefer Java `record` where applicable).
- **Naming Conventions**:
  - Types: `PascalCase` (e.g. `PostgreSqlDialect`, `ModelRegistry`, `StudentDashboard`).
  - Methods and variables: `camelCase` (e.g. `quoteIdentifier`, `selectQuery`).
  - Constants: `UPPER_SNAKE_CASE` (e.g. `DEFAULT_BATCH_SIZE`).
- **Dependencies**: Use constructor injection for all dependencies; avoid field-level injection.
- **Exception Hierarchy**:
  - Root: `GraphOrmException` (unchecked `RuntimeException`).
  - Subtypes: `SchemaException`, `QueryPlanningException`, `DialectException`, `ReadOnlyViewException`, `RecordNotFoundException`.

## 2. Safe Database Access Rules

- **Zero String Concatenation**: Never build SQL by string-concatenating user input.
- **Identifier Quoting**: Always pass identifiers (tables, views, columns) through `SqlDialect.quoteIdentifier()` to avoid SQL keyword collisions and injection.
- **Resource Management**: Always use try-with-resources or Spring's managed connections to ensure connections, statements, and result sets are closed.
- **Pushdown Everything**: Perform filtering, sorting, pagination, and joins in the database. Never pull entire tables into JVM memory.

## 3. "Test Everything as Much as Possible" Mandate

GraphORM enforces a rigorous test-first and comprehensive test coverage protocol:
1. **Mandatory Tests for Every Class & Feature**: No feature, grammar rule, query builder, dialect method, or generator template is complete without exhaustive unit and integration tests.
2. **Dialect Verification**: Every SQL clause, join type, and aggregation expression must be verified for exact SQL syntax and parameter ordering.
3. **Database Verification with Testcontainers**: Never mock the database when testing SQL execution. Use real PostgreSQL Testcontainers instances for both regular tables and view queries.
4. **N+1 Query Regression Tests**: Use SQL statement counters to assert exact query counts (e.g. 1 primary query + 1 batched query per relation).
5. **Compile Generated Code**: Test suites must compile generated Java models and client classes using `javax.tools.JavaCompiler` to catch compilation regressions immediately.
6. **Bug Fix Protocol**: Every bug fix must begin with a failing regression test reproducing the issue before any implementation code is modified.

## 4. Pull Request & Verification Protocol

Before declaring any task or feature complete:
1. Verify that the code compiles cleanly with Java 21 (`mvn clean test`).
2. Verify all unit tests pass with zero warnings.
3. If database changes or queries are modified, run all Testcontainers integration tests.
4. Verify that generated code compiles cleanly.
5. Verify that no foreign dependencies (Spring, DB drivers) have leaked into `graphorm-core`.
