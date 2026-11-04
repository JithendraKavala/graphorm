---
name: graphorm-graphql-engine
description: >-
  Guides automatic GraphQL integration with GraphQL Java and Spring for GraphQL. Covers schema discovery,
  type mapping for models and view models, generic DataFetcher registration, read-only view enforcement,
  mutation handling, argument conversion, and relationship resolution.
  Use when working on graphorm-graphql or Spring for GraphQL wiring.
---

# GraphORM GraphQL Engine

This skill governs how GraphORM connects GraphQL operations to underlying database models and view models using GraphQL Java and Spring for GraphQL.

For implementation roadmap and phase specifications, see:
- [PHASE_BREAKDOWN.md - Phase 10](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [GRAPHQL_INTEGRATION.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/GRAPHQL_INTEGRATION.md)
- [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)

---

## Hybrid Schema-First Strategy

1. **Database Models & Views** defined in `schema.graphorm` (`model`, `view`, `type`).
2. **GraphQL Schema** defined in `.graphqls` files (standard GraphQL SDL).
3. **Metadata Mapping**: GraphORM inspects the GraphQL schema at startup, identifies object types that match GraphORM models or views, and binds root queries and nested relationship fields automatically.

## Generic DataFetcher Registration

GraphORM dynamically registers `DataFetcher` instances in the `GraphQLCodeRegistry`:

### For Regular Models:
- **Root Query Fetchers**:
  - `Query.<model>s`: List fetcher with filtering, sorting, pagination.
  - `Query.<model>(id: ID!)`: Single entity fetcher by primary key.
- **Root Mutation Fetchers**:
  - `Mutation.create<Model>(input: ...)`
  - `Mutation.update<Model>(id: ID!, input: ...)`
  - `Mutation.delete<Model>(id: ID!)`
- **Nested Relation Fetchers**:
  - Delegates to `DataLoader` to batch foreign key lookups across items in a list.

### For View Models (Strictly Read-Only):
- **Root Query Fetchers**:
  - `Query.<view>s`: List fetcher supporting filters, sorting, and pagination across projected view fields.
  - `Query.<view>(id: ID!)`: Single view entity fetcher (if identifiable).
- **Mutations Strictly Excluded**:
  - Never register `create`, `update`, or `delete` fetchers for view models.
  - If a GraphQL schema attempts to declare a mutation returning or modifying a view model without a custom resolver, GraphORM emits a startup validation warning.
- **Nested Views & Relation Fetchers**:
  - Registered for fields linking view models to related models or other view models via `DataLoader`.

## Argument Binding

- Convert GraphQL argument maps (`Map<String, Object>`) to typed filter predicates (`FilterNode`).
- Handle standard pagination arguments: `first`, `after`, `last`, `before`, `limit`, `offset`.
- Handle ordering arguments: `orderBy: { name: ASC, courseCount: DESC }`.
- Validate argument field names against view and model metadata to prevent invalid column access.

## Custom Resolver Coexistence

- If a developer defines a custom `@SchemaMapping` or explicit `DataFetcher` in Spring Boot, GraphORM must yield and NOT overwrite the custom fetcher.
- GraphORM provides helper utilities (`QueryPlanner.plan(env)`) so custom resolvers can still benefit from selection-set analysis, view execution, and dialect generation.
