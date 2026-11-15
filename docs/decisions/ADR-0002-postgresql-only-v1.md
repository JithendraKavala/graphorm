# ADR-0002: PostgreSQL-Only in v1

**Date:** 2024-11-01
**Status:** Accepted
**Deciders:** GraphORM maintainers

---

## Context

GraphORM targets production-quality, complex relational workloads. Supporting multiple databases simultaneously requires either: (a) an abstraction layer that reduces to the lowest common denominator of all supported databases, or (b) separate dialect implementations with significant duplication and testing overhead.

PostgreSQL is a mature, capable, open-source relational database with features (JSONB, arrays, CTEs, window functions, full-text search, LISTEN/NOTIFY, row-level security) that GraphORM intends to expose directly.

## Decision

GraphORM v1 supports PostgreSQL exclusively. No MySQL, SQLite, H2, or other database dialect will be implemented or tested in v1.

This decision applies to:
- SQL query generation
- JDBC result mapping
- Type conversions
- PostgreSQL-specific features (JSONB, arrays, etc.)
- Integration testing

H2, SQLite, and other in-memory databases are explicitly prohibited as substitutes for PostgreSQL in integration tests.

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Multi-database support from the start | Forces lowest-common-denominator SQL; prevents PostgreSQL-specific feature exposure; doubles testing matrix; significantly increases complexity before the core is proven |
| H2-compatible-only SQL for portability | H2 does not support key PostgreSQL features; tests would not validate production behavior |
| Abstract dialect interface from day one | Premature abstraction — there is no second dialect to abstract over yet |

## Consequences

**Positive:**
- SQL generation can be PostgreSQL-specific without compromise
- PostgreSQL-specific features (JSONB, arrays, CTEs, window functions, full-text search) can be first-class
- Integration tests are real PostgreSQL tests — results are production-valid
- Less code, less complexity, faster initial delivery
- The dialect abstraction can be introduced later (ADR-0002-R1) when a real second database is needed

**Negative / Trade-offs:**
- GraphORM cannot be used with MySQL, MariaDB, SQLite, or SQL Server in v1
- Consumers who need multi-database support must wait for v2 or fork

## Compliance

- The parent POM `dependencyManagement` should not include MySQL or SQLite driver dependencies in the production modules (only as optional/excluded in test scope if necessary)
- Code review must reject any SQL that uses non-PostgreSQL syntax in production SQL builders
- All integration tests must use Testcontainers with the official `postgres:16-alpine` image

## Revision History

If a second database dialect is added in a future version, create ADR-0002-R1 to document the abstraction strategy chosen.
