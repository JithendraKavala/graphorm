# ADR-0004: Separate GraphORM Model Schema and GraphQL API Schema

**Date:** 2024-11-01
**Status:** Accepted
**Deciders:** GraphORM maintainers

---

## Context

GraphORM defines models in its own schema language (`.graphorm` files). GraphQL APIs are defined in GraphQL SDL (`.graphqls` files). These are two distinct concerns:

1. **GraphORM schema** — defines database models, field types, relationships, indexes, and storage characteristics
2. **GraphQL schema** — defines the public API surface exposed to API consumers: which queries and mutations exist, what arguments they accept, what types are returned

Conflating these would force every database column to be exposed in the API and would prevent the API from having a different shape than the database model.

## Decision

GraphORM maintains two separate schema files:

1. **`schema.graphorm`** — the GraphORM data model (defines models, fields, relations, database config)
2. **`schema.graphqls`** — the GraphQL API schema (standard GraphQL SDL)

GraphORM inspects both. It uses the GraphORM schema as the source of truth for metadata. It uses the GraphQL schema to determine which fields and operations are exposed. It wires DataFetchers to the GraphQL operations automatically where the mapping is unambiguous.

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Use only GraphQL SDL (define everything in GraphQL) | GraphQL SDL is not designed to express database storage characteristics, indexes, or relationship metadata; forces awkward directives |
| Use only GraphORM schema (generate GraphQL from it) | Loses the ability for the developer to control the API shape independently; forces 1:1 mapping of database to API |
| Use a single schema with namespaced directives | Complexity without clear benefit; a single file becomes difficult to reason about |

## Consequences

**Positive:**
- The database model and API shape can evolve independently
- Sensitive database fields can be excluded from the API without schema gymnastics
- The API can present computed or aggregated fields that don't directly correspond to a database column
- The GraphQL schema is standard SDL and can be used with any GraphQL tooling

**Negative / Trade-offs:**
- Two schema files to maintain
- Changes to the data model may require corresponding changes to the GraphQL schema
- The mapping between the two schemas must be explicit in ambiguous cases

## Compliance

- `graphorm-graphql` must not read from the GraphORM schema directly — it must work through metadata objects provided by `graphorm-core`
- DataFetcher registration must be driven by GraphQL schema inspection, not GraphORM schema scanning
