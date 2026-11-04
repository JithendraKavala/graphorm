---
name: graphorm-testing
description: >-
  Establishes testing standards, patterns, and requirements across all GraphORM modules.
  Enforces the "Test Everything as Much as Possible" protocol covering unit tests, compiler tests,
  parser diagnostics, SQL dialect verification, view model query tests, Testcontainers PostgreSQL integration tests,
  GraphQL execution tests, and N+1 query regression testing. Use whenever writing or validating tests.
---

# GraphORM Testing & Quality Engineering

This skill defines the testing methodology, test suites, and quality standards for GraphORM.

## The "Test Everything as Much as Possible" Mandate

Because GraphORM is an infrastructure-level ORM and query execution framework, reliability and correctness are paramount. **Everything must be tested as thoroughly as possible**:
1. **Target >90% Coverage**: Strive for maximum branch and line coverage across `graphorm-core`, `graphorm-schema`, `graphorm-jdbc`, `graphorm-generator`, and `graphorm-graphql`.
2. **Zero Untested Edge Cases**: Every AST node, token type, scalar mapping, dialect operator, error diagnostic, and view join permutation must have dedicated tests.
3. **No Mocking of Database Execution**: SQL execution, transaction rollbacks, connection management, and view queries must run against real PostgreSQL instances via Testcontainers.
4. **Compile-Check All Generated Code**: All templates in `graphorm-generator` must compile cleanly in test suites using `javax.tools.JavaCompiler`.
5. **N+1 Regression Zero-Tolerance**: All relation and nested view fetchers must assert exact SQL execution counts using query statement interceptors.
6. **Strict Definition of Done Compliance**: All testing and verification gates must comply with [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md).

---

## Testing Hierarchy & Matrix

| Level | Scope | Tech Stack | Requirements |
|---|---|---|---|
| **Unit Tests** | Parser, AST, Lexer, Core abstractions | JUnit 5, AssertJ | Fast, isolated, test every token, keyword, valid schema, syntax error, and diagnostic line/column coordinates |
| **Compiler Verification** | Generated Java Models, Views, Clients | `javax.tools.JavaCompiler` | Validate generated records compile cleanly with zero errors/warnings |
| **Dialect Tests** | SQL Generation & AST Translation | JUnit 5 | Test exact SQL strings, parameter ordering, table qualifications, quotes, and joins/aggregations against mock ASTs |
| **Integration Tests** | Real DB Execution & Transactions | JUnit 5, Testcontainers (PostgreSQL) | Execute real DDL/DML, test transactions, verify row mapping for regular tables and database-backed views |
| **View Engine Tests** | Dynamic Joins & Aggregations | JUnit 5, Testcontainers | Verify dynamic SQL generation for GraphORM-defined views, pushdown filtering (`WHERE`), grouping (`GROUP BY`), and `HAVING` |
| **GraphQL E2E Tests** | Full Selection to SQL Execution | Spring for GraphQL Test / `GraphQlTester` | Test queries, verify only requested columns are fetched, verify N+1 batching, verify view read-only semantics |

---

## Detailed Testing Standards by Area

### 1. Schema Parser & Validator Tests (`graphorm-schema`)
- **Positive Grammar Tests**: Test full parsing of models, database-backed views, virtual GraphORM-defined views, composite types, enums, attributes, and datasources.
- **Negative Diagnostics**: Test error reporting with precise line and column assertions:
  ```java
  List<Diagnostic> errors = validator.validate(schemaAst);
  assertThat(errors).hasSize(1);
  assertThat(errors.get(0).line()).isEqualTo(14);
  assertThat(errors.get(0).message()).contains("View 'StudentDashboard' references unknown source model 'InvalidModel'");
  ```
- **Read-Only Invariant Tests**: Verify errors when `@default(autoincrement())` or write mutations are applied to views.
- **Cycle Detection Tests**: Assert that circular view references (e.g., View A -> View B -> View A) are detected and reported.

### 2. Code Generation Tests (`graphorm-generator`)
- **Snapshot / Golden File Tests**: Compare generated Java code against expected reference sources.
- **Compilation Verification**:
  ```java
  JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
  int result = compiler.run(null, null, null, generatedSourceFile.toFile().getAbsolutePath());
  assertThat(result).isZero(); // Zero means successful compilation
  ```
- **Determinism Tests**: Verify byte-for-byte identical output across repeated generator runs.

### 3. Dialect & SQL Generation Tests (`graphorm-jdbc`)
- Assert exact generated SQL and parameter binding arrays for all operators:
  ```java
  BoundSql bound = dialect.buildSelectSql(selectQuery);
  assertThat(bound.sql()).isEqualTo("SELECT \"s\".\"id\", \"s\".\"name\" FROM \"students\" \"s\" WHERE \"s\".\"department_id\" = ?");
  assertThat(bound.parameters()).containsExactly(10);
  ```
- Verify dialect handles table qualification, joins, aliases, aggregations (`COUNT`, `SUM`, `AVG`, `MIN`, `MAX`), and pagination (`LIMIT ? OFFSET ?`).

### 4. Integration Tests with Testcontainers (`graphorm-jdbc`, `graphorm-examples`)
- Use container: `postgres:16-alpine`.
- Test real CRUD operations, transactions, and rollback behavior.
- Test queries against physical database views (`CREATE VIEW ...`).
- Test dynamic GraphORM view queries with multi-table joins and aggregations against real PostgreSQL tables.
- Verify result mapping into Java records and view projections via `DynamicRowMapper`.

### 5. GraphQL Selection Set & N+1 Prevention Tests (`graphorm-graphql`)
- **Statement Tracking**:
  ```java
  sqlTracker.reset();
  graphQlTester.document("query { studentDashboards { studentName department { name } } }").execute();
  assertThat(sqlTracker.getCount()).isEqualTo(2); // 1 for view query, 1 batched for departments
  ```
- **Selection-Aware Projection**: Assert that database queries select **only** requested columns, auto-injecting hidden foreign keys while omitting unrequested fields.
- **Mutation Suppression Verification**: Assert that GraphQL schema inspection contains **no** mutation fields for view models.

---

## Bug Fix & Regression Protocol
- **Reproduce First**: Every bug fix must begin with a dedicated failing regression test reproducing the exact issue.
- **Verify Fix**: Ensure the test passes once the fix is applied and does not break existing test suites.
- **Track Progress**: Update [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md) with test results.
