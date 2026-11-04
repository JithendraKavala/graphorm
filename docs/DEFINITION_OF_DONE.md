# GraphORM: Definition of Done (DoD)

This document establishes the non-negotiable quality and completion standards for every phase, task, and pull request in GraphORM.

---

## 1. Project-Wide Quality Invariants

A phase or task cannot be marked as **Completed** merely because the code compiles or tests pass locally. In GraphORM, completion requires meeting all the criteria below.

### 1.1 Implementation Integrity
- **100% Feature Delivery**: All functionality promised in the phase specification and task description is completely implemented.
- **Zero Stub or Mock Placeholders**: No placeholder methods (`throw new UnsupportedOperationException("TODO")`), dummy implementations, or fake mocks remain in production code.
- **Zero Foreign Dependencies in Core**: `graphorm-core`, `graphorm-schema`, `graphorm-generator`, and `graphorm-jdbc` must remain free of Spring dependencies. `graphorm-core` must have zero JDBC driver or database dependencies.
- **Immutable Metadata**: All schema AST nodes and runtime metadata objects (`ModelMetadata`, `ViewModelMetadata`, `FieldMetadata`, `RelationMetadata`, `CompositeTypeMetadata`) are strictly immutable once parsed and registered.
- **Read-Only View Model Enforcement**: View models (`view`) are strictly read-only. Never expose write operations (`create`, `update`, `delete`) or write DTOs for view models in Java clients, GraphQL schemas, or JDBC execution builders.
- **Unified Query Architecture**: Regular models and view models share the same unified query AST, SQL dialect generation, and query planner. No parallel or duplicate query engines are permitted.

### 1.2 Testing & Quality Gates
- **Comprehensive Unit Testing**: Every new class, parser rule, query type, builder, dialect method, and validator rule has dedicated unit tests covering both happy paths and edge cases (nullability, empty inputs, malformed structures).
- **Dialect SQL Precision Verification**: 100% of generated SQL strings, aliases, table qualifications, parameter placeholders (`?`), and parameter bindings are asserted with exact string matches in dialect tests.
- **Real Database Integration Testing**: All database operations (CRUD, joins, views, aggregations, transactions, and pagination) are verified against a real PostgreSQL 15+ container using Testcontainers. Database execution must never be mocked away.
- **N+1 Query Elimination Assertions**: Every relationship resolver and nested view fetcher must assert exact SQL execution counts using query statement interceptors:
  - 1 query for the primary model/view list.
  - 1 batched query for each traversed relationship (`WHERE foreign_key IN (?, ?, ...)`).
- **Generated Code Compilation**: All code generation templates in `graphorm-generator` are verified by compiling the generated Java source code in test suites using `javax.tools.JavaCompiler` (JSR-199).
- **Zero Test Failures & Clean Build**: All test suites must pass cleanly (`mvn clean test-compile test`) with zero failures, zero errors, and zero compiler warnings.

### 1.3 Documentation & Architecture Synchronization
- **API Documentation**: All public classes, interfaces, records, and methods have clear Javadoc explaining their contract, parameters, return values, and thrown domain exceptions.
- **Architecture Synchronization**: Any architectural modification or new design decision is recorded in `docs/ARCHITECTURAL_DECISIONS.md` before implementation.
- **Documentation Updates**: Relevant documentation files (`docs/*.md`) are updated to reflect new functionality, schema syntax additions, or configuration properties.
- **Roadmap & Progress Tracking**: `docs/IMPLEMENTATION_ROADMAP.md` and `docs/PROGRESS_TRACKER.md` are updated to reflect completed tasks with links to PRs and test classes.

### 1.4 Security & Operational Safety
- **100% SQL Parameterization**: All query values must be bound as positional parameters (`?`). Zero SQL string concatenation of values.
- **Identifier Quoting & Validation**: All table names, view names, column names, and aliases are validated against `ModelRegistry` and escaped through `SqlDialect.quoteIdentifier()`.
- **Resource Cleanup**: All JDBC connections, prepared statements, and result sets are safely managed via try-with-resources or Spring transaction synchronization to prevent connection leaks.
- **Informative Error Diagnostics**: Exceptions provide actionable context, including schema file names, line/column coordinates, or SQL query snippets, without leaking credentials or sensitive database details.

---

## 2. Phase-Level Completion Checklist

Before declaring any Phase complete and proceeding to the next phase, the engineering team / agent must verify:

| Criteria | Verification Method | Required Status |
|---|---|---|
| **All Phase Tasks Completed** | Checked against `docs/PHASE_BREAKDOWN.md` | 100% Completed |
| **Clean Build** | `mvn clean compile` | Exit code 0, 0 warnings |
| **Unit Test Suite** | `mvn test` | 100% passing |
| **Integration Test Suite** | `mvn verify` (Testcontainers PostgreSQL) | 100% passing |
| **Code Generation Suite** | JavaCompiler compilation of generated code | Clean compilation |
| **N+1 Query Check** | Statement count assertion on nested queries | Asserted exact query count |
| **Read-Only Invariant** | Schema validation & code generator tests | Write mutations blocked |
| **Documentation Updated** | `docs/` and `.agents/skills/` synchronized | Synchronized |
| **Progress Tracker Updated** | `docs/PROGRESS_TRACKER.md` updated | Updated |
| **End-to-End Milestone Verified** | Runnable verification sample or test | Fully verified |
