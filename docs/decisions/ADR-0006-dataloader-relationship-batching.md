# ADR-0006: DataLoader-Based Relationship Batching

**Date:** 2024-11-01
**Status:** Accepted
**Deciders:** GraphORM maintainers

---

## Context

In a GraphQL API, resolving relationships naively results in N+1 queries: one query to fetch the parent list, then one query per parent to fetch its children. For a list of 100 orders, this would issue 101 queries instead of 2.

The GraphQL Java ecosystem provides `DataLoader` as the standard solution: relationship resolvers return `CompletableFuture`, and a batch function is called once per request with all parent IDs collected from the selection set.

## Decision

GraphORM uses `graphql-java`'s `DataLoader` (via `DataLoaderRegistry`) for all relationship resolution.

For every relationship defined in the GraphORM schema, GraphORM registers a batched `DataLoader` that:

1. Collects all parent IDs from across the request
2. Issues a single `IN (?)` query for all child records at once
3. Groups results by parent ID
4. Returns the correct children to each parent's resolver

This applies to:
- One-to-many relationships (`IN` query on foreign key)
- Many-to-one relationships (`IN` query on referenced primary key)
- Many-to-many relationships (join table `IN` query)

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Eager JOIN fetching for all relationships | Cannot work correctly with pagination on nested relationships; produces Cartesian products for multi-level nesting |
| Per-parent query (naive N+1) | Unacceptable in production — 100 parents = 100 extra queries |
| Custom batching without DataLoader | DataLoader is the established standard in graphql-java; reinventing it without benefit |
| GraphQL persisted queries with pre-joined SQL | Requires static query shapes; incompatible with dynamic GraphQL selection sets |

## Consequences

**Positive:**
- N+1 queries are eliminated by design for all standard relationship types
- Performance scales with the number of unique parent sets, not the number of parents
- Consistent batching behavior across all models

**Negative / Trade-offs:**
- `CompletableFuture` introduces asynchronous programming complexity in the GraphQL layer
- DataLoader batching adds latency for single-item queries (the batch wait)
- Complex nested relationships with filters require careful plan construction to avoid incorrect grouping

## Compliance

- `graphorm-graphql` must register a `DataLoader` for every relationship field, not issue direct queries
- N+1 regression tests must assert query counts (not just result correctness) for all relationship-loading paths
- Query count tests must remain in CI permanently and must not be weakened
