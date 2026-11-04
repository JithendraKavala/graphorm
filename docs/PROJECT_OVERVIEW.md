# GraphORM: Project Overview

## 1. Executive Summary

**GraphORM** is an open-source Java framework inspired by Prisma that bridges relational databases with automatic GraphQL integration in Spring Boot applications.

In modern Spring Boot and GraphQL architectures, developers spend an excessive amount of engineering effort writing and maintaining redundant boilerplate:
- Entity/model classes and DTOs
- Read-only projection types and view models
- Data Access Objects (DAOs) and Repositories
- JDBC RowMappers, ResultSet converters, and manual multi-table joins
- GraphQL DataFetchers, DataResolvers, and argument binders
- Relationship and batch resolvers (DataLoader setup)
- Repetitive CRUD and query implementations

As entities and business requirements grow in number and complexity, maintaining these distinct layers becomes error-prone and inefficient. GraphORM eliminates this repetitive labor by providing a declarative, schema-driven framework that auto-generates models, provides a fluent type-safe database client, dynamically registers GraphQL DataFetchers, and optimizes database queries based on incoming GraphQL selection sets.

---

## 2. Model Categories

A core architectural requirement of GraphORM is distinguishing between three primary schema constructs:

1. **Regular Models (`model`)**:
   - Represent physical database tables.
   - Support full CRUD operations (`findUnique`, `findMany`, `create`, `update`, `delete`).
   - Define primary keys, indexes, unique constraints, and bidirectional relationships.
2. **View Models (`view`)**:
   - Represent read-only data projections combining multiple models, relationships, aggregations, and computed fields.
   - Support two implementation strategies:
     - **Database-Backed Views**: Maps directly to existing database views or materialized views in PostgreSQL, supporting schema introspection and automatic read-only GraphQL exposure.
     - **GraphORM-Defined Views**: Declared virtually in `schema.graphorm` without requiring a physical database view. Translated dynamically into optimized SQL (joins, subqueries, CTEs) without manual DTOs, DAOs, or resolvers.
   - Strictly read-only: write operations (`create`, `update`, `delete`) are prohibited by default at schema, client, and API levels.
3. **Composite Types (`type`)**:
   - Represent reusable, nested structural types (e.g., `Address`, `AuditInfo`, `Money`).
   - Can be embedded inside regular models or view models without being standalone database tables.

---

## 3. Core Vision & Developer Workflow

GraphORM brings the developer ergonomics of modern schema-driven frameworks to the Java and Spring Boot ecosystem:

```text
+-----------------------+     +-----------------------+
|  schema.graphorm      |     |  schema.graphqls      |
|  - Models (Tables)    |     |  (Public API SDL)     |
|  - Views (Projections)|     |                       |
|  - Composite Types    |     |                       |
+-----------------------+     +-----------------------+
            \                             /
             \                           /
              +-------------------------+
              |   GraphORM Generator    |
              +-------------------------+
                           |
                           v
              +-------------------------+
              | - Immutable Java Models |
              | - Read-Only View Models |
              | - Type-safe Client API  |
              | - Filter & Input DTOs   |
              | - Runtime Metadata      |
              +-------------------------+
                           |
                           v
              +-------------------------+
              | Spring Boot Application |
              | - Auto DataFetchers     |
              | - Selection-Set SQL     |
              | - Batched DataLoaders   |
              | - Unified Query Planner |
              +-------------------------+
```

### The Developer Experience
1. **Define data structures** in `schema.graphorm` using a clean, Prisma-like DSL declaring physical `model`s, declarative `view` models, and reusable composite `type`s.
2. **Define the public GraphQL API** in `schema.graphqls` using standard GraphQL SDL.
3. **Run generation**: Automatically generate Java record models, view projection records, input types, and a fluent database client (`db.student().findMany(...)`, `db.studentDashboard().findMany(...)`).
4. **Zero-Boilerplate Runtime**: GraphORM registers root queries, mutations (for regular models only), relationship resolvers, and view queries automatically, generating SQL that projects only the requested columns, resolves multi-table joins, and batches lookups to eliminate N+1 queries.

---

## 4. Guiding Principles

- **Schema-First**: Data structures, views, and relationships are declared once in declarative schemas.
- **Unified Query Planning**: Regular models and view models share the same query planning and execution engine.
- **Selective Projection**: The database engine fetches only what the client requests—never loading entire tables into Java memory.
- **Read-Only Safety**: View models are strictly immutable and read-only by default.
- **Safety First**: Parameterized SQL, type-safe APIs, compile-time verification, and identifier quoting.
- **Framework Modularity**: Core abstractions are decoupled from Spring and specific database engines.

---

## 5. Architectural Documentation Map

For deep technical specifications on each subsystem, refer to the dedicated design documents:

- **Architecture & Data Flow**: [ARCHITECTURE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/ARCHITECTURE.md)
- **Implementation Roadmap**: [IMPLEMENTATION_ROADMAP.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/IMPLEMENTATION_ROADMAP.md)
- **Phase-by-Phase Task Breakdown**: [PHASE_BREAKDOWN.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- **Live Progress Tracker**: [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- **Quality Standards & DoD**: [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)
- **Architecture Decision Records**: [ARCHITECTURAL_DECISIONS.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/ARCHITECTURAL_DECISIONS.md)
- **View Model Architecture**: [VIEW_MODEL_DESIGN.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/VIEW_MODEL_DESIGN.md)
- **Schema Language Specification**: [SCHEMA_LANGUAGE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/SCHEMA_LANGUAGE.md)
- **Query Engine & SQL Generation**: [QUERY_ENGINE_DESIGN.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/QUERY_ENGINE_DESIGN.md)
- **GraphQL Integration & DataFetchers**: [GRAPHQL_INTEGRATION.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/GRAPHQL_INTEGRATION.md)
- **Code Generation Engine**: [CODE_GENERATION.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/CODE_GENERATION.md)
- **Security & Access Control**: [SECURITY.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/SECURITY.md)
- **Testing & Quality Engineering**: [TESTING_STRATEGY.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/TESTING_STRATEGY.md)
- **Module Structure & Boundaries**: [MODULE_STRUCTURE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/MODULE_STRUCTURE.md)
- **Technology Stack**: [TECH_STACK.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/TECH_STACK.md)
