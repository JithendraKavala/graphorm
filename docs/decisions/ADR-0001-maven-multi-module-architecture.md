# ADR-0001: Maven Multi-Module Architecture

**Date:** 2024-11-01
**Status:** Accepted
**Deciders:** GraphORM maintainers

---

## Context

GraphORM is a framework composed of several distinct concerns: schema parsing, query planning, SQL execution, GraphQL integration, code generation, Spring Boot integration, and testing utilities. These concerns have different dependency profiles and must be usable independently by consumers.

A single-module Maven project would couple all concerns together, prevent consumers from selectively depending on only the components they need, and make it difficult to enforce architectural boundaries.

## Decision

GraphORM is structured as a Maven multi-module project with the following modules:

| Module | Responsibility |
|--------|---------------|
| `graphorm-core` | Core interfaces, exceptions, metadata abstractions, shared types |
| `graphorm-schema` | Schema language parser, AST, semantic validation, metadata conversion |
| `graphorm-generator` | Compile-time Java model and client code generation |
| `graphorm-jdbc` | PostgreSQL JDBC execution, connection handling, result mapping |
| `graphorm-query-engine` | Query representation, filters, sorting, pagination, SQL compilation |
| `graphorm-graphql` | GraphQL schema mapping, DataFetcher registration, DataLoader integration |
| `graphorm-spring-boot-starter` | Spring Boot auto-configuration and lifecycle management |
| `graphorm-cli` | Schema validation and code generation CLI |
| `graphorm-testkit` | Reusable test utilities, Testcontainers integration, query counters |
| `graphorm-benchmarks` | JMH benchmarks |
| `examples/nexus-commerce` | Reference enterprise application |

The dependency direction is strictly enforced:

```
graphorm-core (no internal deps)
    ↑
graphorm-schema          graphorm-jdbc
    ↑                        ↑
graphorm-generator   graphorm-query-engine
                             ↑
                      graphorm-graphql
                             ↑
                  graphorm-spring-boot-starter
```

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| Single Maven module | Prevents selective dependency; couples all concerns; difficult to enforce boundaries |
| Gradle multi-project build | Gradle is not the fixed technology decision; Maven was chosen for consistency and ecosystem familiarity |
| OSGi bundle structure | Unnecessary complexity for this use case |

## Consequences

**Positive:**
- Each module has a clearly defined responsibility
- Consumers can depend only on the modules they need
- Circular dependencies can be detected automatically
- Module boundaries enforce architectural discipline
- Modules can be versioned and released independently if needed

**Negative / Trade-offs:**
- More POM files to maintain
- Initial setup is more complex than a single module
- Refactoring across module boundaries requires careful coordination

## Compliance

- Maven's reactor build enforces dependency declarations
- `mvn dependency:analyze` can detect undeclared or unused dependencies
- Code review enforces that imports from one module do not reference another module's internals
