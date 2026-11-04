---
name: graphorm-security
description: >-
  Guides safe implementation of SQL parameterization, identifier validation, input validation,
  field-level authorization, GraphQL complexity limits, and sensitive data protection.
  Use when designing database queries, exposing GraphQL fields, or implementing security layers.
---

# GraphORM Security & Access Control

This skill establishes the security protocols for GraphORM to guarantee safe execution in production environments.

For implementation roadmap and phase specifications, see:
- [PHASE_BREAKDOWN.md - Phase 15](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [SECURITY.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/SECURITY.md)
- [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)

---

## Threat Model & Defenses

### 1. SQL Injection (High Severity)
- **Rule**: 100% of values must be passed as parameterized positional variables (`?`) via JDBC `PreparedStatement`.
- **Identifiers**: Column names and table names cannot be parameterized in standard SQL. Therefore, all identifiers MUST be validated against the registered `ModelMetadata` and properly quoted using `SqlDialect.quoteIdentifier()`.
- **Static Analysis**: Any code constructing SQL using `+` or `String.format` with arbitrary user inputs will fail automated security review.

### 2. GraphQL Denial of Service (Complexity & Depth Attacks)
- Malicious clients can craft deeply nested circular queries:
  ```graphql
  query {
      students { department { students { department { ... } } } }
  }
  ```
- **Defense**: Integrate GraphQL Java's `MaxQueryDepthInstrumentation` (configurable, default: 8) and `MaxQueryComplexityInstrumentation`.

### 3. Mass Assignment & Sensitive Field Exposure
- Not all database columns should be exposed in GraphQL or writable via input types.
- The GraphQL SDL acts as the whitelist boundary. If a database model has a `passwordHash` column, but it is omitted from the GraphQL `type Student`, it must NEVER be exposed.
- Input types must strictly validate allowed fields.

### 4. Transaction Isolation & Connection Leak Prevention
- Always execute database operations inside structured try-with-resources blocks or Spring's managed transaction boundaries.
- Ensure connections, statements, and result sets are deterministically closed.
