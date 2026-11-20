# ADR-0005: Shared Query Engine for GraphQL and Java Client

**Date:** 2024-11-01
**Status:** Accepted
**Deciders:** GraphORM maintainers

---

## Context

GraphORM exposes three execution interfaces: the GraphQL API, the Java client API, and the native SQL API. If these interfaces each had separate query execution paths, the same logic would be duplicated, bugs would be fixed inconsistently, and testing would become exponentially more complex.

## Decision

All three execution interfaces share the same underlying query engine (`graphorm-query-engine`).

The execution flow is:

```
GraphQL DataFetcher → QueryPlan → QueryEngine → JdbcExecutor → ResultSet
Java Client API    → QueryPlan → QueryEngine → JdbcExecutor → ResultSet
Native SQL API     →                           JdbcExecutor → ResultSet
```

The query engine receives a `QueryPlan` (a structured representation of what to query) and delegates SQL compilation and JDBC execution to `graphorm-jdbc`. Neither the GraphQL layer nor the Java client layer issue SQL directly.

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Separate SQL generators per interface | Duplication of logic, inconsistent behavior, exponential test surface |
| GraphQL layer calling JDBC directly | Leaks JDBC details into the GraphQL layer; violates module separation |
| Java client built on top of GraphQL | Introduces unnecessary indirection; Java client performance would be limited by GraphQL parsing overhead |

## Consequences

**Positive:**
- A bug fixed in the query engine is fixed for all three interfaces simultaneously
- SQL safety guarantees apply consistently across all interfaces
- Performance optimizations apply universally
- Testing a query plan tests all three interfaces at once

**Negative / Trade-offs:**
- The `QueryPlan` representation must be expressive enough to serve all three interfaces
- Changes to `QueryPlan` can have broad impact — backward compatibility must be maintained carefully

## Compliance

- `graphorm-graphql` must not import any class from `graphorm-jdbc`
- SQL generation code must exist exclusively in `graphorm-jdbc`
- `graphorm-query-engine` owns `QueryPlan` and the query planning logic
- Code review must reject any direct JDBC calls in the GraphQL or Java client modules
