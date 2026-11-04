# GraphORM: Architecture Decision Records (ADRs)

This document records the foundational architectural decisions for GraphORM. Each record details the context, considered options, trade-offs, recommended choice, and downstream consequences.

---

## Index of Architecture Decision Records

- [ADR-001: Schema Language Design & Parser Selection](#adr-001-schema-language-design--parser-selection)
- [ADR-002: GraphQL Schema vs Database Schema Responsibilities](#adr-002-graphql-schema-vs-database-schema-responsibilities)
- [ADR-003: Runtime Metadata Representation & Immutability](#adr-003-runtime-metadata-representation--immutability)
- [ADR-004: Internal Query AST & Composable Query Tree](#adr-004-internal-query-ast--composable-query-tree)
- [ADR-005: Unified SQL Query Planning for Models & View Models](#adr-005-unified-sql-query-planning-for-models--view-models)
- [ADR-006: Direct JDBC Execution Over JPA/Hibernate](#adr-006-direct-jdbc-execution-over-jpahibernate)
- [ADR-007: Result Mapping Strategy (DynamicRowMapper vs Static Proxies)](#adr-007-result-mapping-strategy-dynamicrowmapper-vs-static-proxies)
- [ADR-008: Relationship Loading Strategy & N+1 Prevention](#adr-008-relationship-loading-strategy--n1-prevention)
- [ADR-009: DataLoader Integration & Hidden Dependency Injection](#adr-009-dataloader-integration--hidden-dependency-injection)
- [ADR-010: Two-Tiered View Model Strategy (DB-Backed & GraphORM-Defined)](#adr-010-two-tiered-view-model-strategy-db-backed--graphorm-defined)
- [ADR-011: SQL Dialect Abstraction & PostgreSQL Target](#adr-011-sql-dialect-abstraction--postgresql-target)
- [ADR-012: Code Generation Strategy (JavaPoet Compile-Time Generation)](#adr-012-code-generation-strategy-javapoet-compile-time-generation)
- [ADR-013: Spring Boot Auto-Configuration & Modular Independence](#adr-013-spring-boot-auto-configuration--modular-independence)
- [ADR-014: Transaction Management & Spring PlatformTransactionManager](#adr-014-transaction-management--spring-platformtransactionmanager)
- [ADR-015: Error Handling Hierarchy & Field-Level Authorization](#adr-015-error-handling-hierarchy--field-level-authorization)

---

## ADR-001: Schema Language Design & Parser Selection

### Context
GraphORM requires a declarative schema language to specify physical tables (`model`), read-only views (`view`), composite types (`type`), relationships, and configurations. We must decide whether to create a custom schema grammar or use an existing format (JSON, YAML, GraphQL SDL), and choose an implementation approach for the parser.

### Options Considered
1. **Prisma-Inspired DSL with Custom Hand-Crafted Recursive Descent Parser**:
   - *Pros*: Zero external dependencies, precise control over error diagnostics, line/column tracking, small footprint.
   - *Cons*: Requires writing tokenization, AST construction, and error recovery from scratch.
2. **Prisma-Inspired DSL with ANTLR4 Parser Generator**:
   - *Pros*: Formal grammar definition (`.g4`), robust lexer/parser generator, standard AST visitor pattern, battle-tested in compilers.
   - *Cons*: Runtime dependency on `antlr4-runtime`, build step generation overhead.
3. **YAML / JSON Configuration**:
   - *Pros*: Standard parsers exist (Jackson, SnakeYAML).
   - *Cons*: Poor developer ergonomics, extremely verbose for complex relationship and join definitions, lack of type-declaration aesthetics.
4. **Pure GraphQL SDL Extended with Custom Directives**:
   - *Pros*: Single schema file.
   - *Cons*: Forces database physical modeling into GraphQL SDL, clutters public API schemas, awkward syntax for joins and aggregations.

### Recommended Choice
**Option 2: Prisma-Inspired DSL (`schema.graphorm`) built using an ANTLR4 Grammar (`GraphOrm.g4`)**.

### Consequences
- ANTLR4 generates clean, fast lexers and parsers in `graphorm-schema`.
- Precise line/column error diagnostics are automatically provided via ANTLR syntax error listeners.
- The grammar cleanly separates lexical tokens from syntactic rules for `model`, `view`, and `type` blocks.
- `graphorm-schema` depends only on `antlr4-runtime` and `graphorm-core`.

---

## ADR-002: GraphQL Schema vs Database Schema Responsibilities

### Context
Should `schema.graphorm` automatically generate the public GraphQL SDL, or should the developer maintain a separate `schema.graphqls`?

### Options Considered
1. **Single Schema (GraphORM generates public GraphQL schema automatically)**:
   - *Pros*: Developer writes one schema only.
   - *Cons*: Unintended exposure of private columns (passwords, internal flags); tightly couples public API contract to database schema; difficult to customize GraphQL field names or deprecations.
2. **Separate Dual Schemas (Hybrid Approach)**:
   - `schema.graphorm` defines data models, views, and database structures.
   - `schema.graphqls` defines the public GraphQL contract.
   - *Pros*: Clear security boundaries; fields not in GraphQL SDL are never exposed; independent schema evolution; idiomatic to Spring for GraphQL.
   - *Cons*: Developers maintain two files (mitigated by CLI generator creating starter `.graphqls` files).

### Recommended Choice
**Option 2: Dual Hybrid Schema-First Architecture**.

### Consequences
- `GraphOrmGraphQlRegistrar` maps GraphQL types against `ModelRegistry`.
- Database fields not declared in `schema.graphqls` remain strictly internal.
- View models can be selectively exposed to public clients.

---

## ADR-003: Runtime Metadata Representation & Immutability

### Context
How should parsed schema information be stored, accessed, and passed across the framework at runtime?

### Options Considered
1. **Mutable Java Beans with Reflection**:
   - *Pros*: Easy to modify during startup.
   - *Cons*: Concurrency risks, mutable state bugs, slow reflection at runtime.
2. **Immutable Java 21 Records / Final Classes (`ModelRegistry`)**:
   - *Pros*: Thread-safe by design, zero concurrency overhead, easily cached, pattern matching support, clear contract.
   - *Cons*: Must be fully constructed in a single validation/build phase.

### Recommended Choice
**Option 2: Immutable Java 21 Records / Classes in `ModelRegistry`**.

### Consequences
- `ModelMetadata`, `ViewModelMetadata`, `FieldMetadata`, `RelationMetadata`, `CompositeTypeMetadata` are immutable records.
- `ModelRegistry` is built once during startup by `SchemaToMetadataTransformer` and cannot be modified afterwards.
- Runtime query execution reads directly from memory with zero reflection overhead.

---

## ADR-004: Internal Query AST & Composable Query Tree

### Context
How should database operations be represented internally before generating dialect-specific SQL?

### Options Considered
1. **Direct String Interpolation / Fragment Templates**:
   - *Pros*: Simple to start.
   - *Cons*: Severe security vulnerability (SQL injection), zero ability to inspect, prune, optimize, or adapt across SQL dialects.
2. **jOOQ / QueryDSL Integration**:
   - *Pros*: Existing SQL DSL libraries.
   - *Cons*: Heavy external dependencies, commercial licensing issues (jOOQ), difficult to adapt for custom view models and GraphQL selection-set pruning.
3. **Dedicated GraphORM Query AST (`SelectQuery`, `ProjectedColumn`, `JoinClause`, `WherePredicate`)**:
   - *Pros*: Zero external dependencies, specifically designed for selection pruning, hidden dependency injection, and view join trees.
   - *Cons*: Must maintain our own AST classes and dialect builders.

### Recommended Choice
**Option 3: Dedicated GraphORM Query AST in `graphorm-core`**.

### Consequences
- Clear separation between query intent (`SelectQuery`) and SQL representation (`BoundSql`).
- Query planner can optimize, prune joins, and inject hidden columns before handing the AST to the dialect generator.

---

## ADR-005: Unified SQL Query Planning for Models & View Models

### Context
Should regular models and view models use separate query planners and builders, or a unified planning engine?

### Options Considered
1. **Bifurcated Engines**:
   - Simple table queries handled by a basic TableQueryEngine; complex views handled by a distinct ViewEngine.
   - *Pros*: Simple table engine is easy to write.
   - *Cons*: Duplication of filtering, pagination, sorting, row mapping, and dialect logic; violates Rule 12 in `AGENTS.md`.
2. **Unified Query Planner**:
   - A single `QueryPlanner` treats regular models as single-table queries and views as multi-table join/aggregation queries under the same `SelectQuery` AST.
   - *Pros*: Code reuse, uniform dialect output, consistent pushdown optimization, single maintenance surface.
   - *Cons*: Query AST must support joins and aggregations from day one.

### Recommended Choice
**Option 2: Unified Query Planner for Both Models and Views**.

### Consequences
- Single query planning pipeline in `graphorm-jdbc` and `graphorm-graphql`.
- Regular models simply have zero joins and single-table sources in `SelectQuery`.

---

## ADR-006: Direct JDBC Execution Over JPA/Hibernate

### Context
Should GraphORM use JPA / Hibernate as its underlying persistence layer or execute directly via JDBC?

### Options Considered
1. **JPA / Hibernate Wrapper**:
   - *Pros*: Built-in ORM features.
   - *Cons*: Heavy memory footprint, first-level session cache conflicts with stateless GraphQL execution, impossible to dynamically project arbitrary columns without tedious Tuple/DTO queries.
2. **Direct Pure JDBC (`javax.sql.DataSource`)**:
   - *Pros*: Full control over generated SQL, dynamic column projection (`SELECT s.id, s.name`), predictable performance, zero ORM caching friction, compatible with Virtual Threads.
   - *Cons*: Must implement statement execution, parameter binding, and result mapping.

### Recommended Choice
**Option 2: Direct Pure JDBC (`javax.sql.DataSource`)**.

### Consequences
- High-performance, lightweight execution with zero session state.
- Predictable execution plans and zero magic Hibernate proxies.

---

## ADR-007: Result Mapping Strategy (DynamicRowMapper vs Static Proxies)

### Context
How should JDBC `ResultSet` rows be converted into Java objects, especially when column projections vary dynamically?

### Options Considered
1. **Dynamic Bytecode Generation / CGLIB Proxies**:
   - *Pros*: Creates custom classes at runtime.
   - *Cons*: Fragile, incompatible with GraalVM native images, complex debugging.
2. **DynamicRowMapper with Reflection / Record Constructor Mapping**:
   - *Pros*: Maps ResultSet columns dynamically to Record components or Maps based on the query projection.
   - *Cons*: Small reflection overhead on constructor invocation (mitigated by `MethodHandle` caching).
3. **Map<String, Object> Output for GraphQL**:
   - *Pros*: GraphQL Java natively accepts Maps; zero reflection needed for GraphQL DataFetchers.
   - *Cons*: Java client needs strongly typed records.

### Recommended Choice
**Hybrid Mapping**:
- For GraphQL DataFetchers: Map directly to `Map<String, Object>` or dynamically mapped records using cached `MethodHandle` constructors.
- For Java Client: Generated immutable Java records mapped via cached constructor delegates.

### Consequences
- High throughput, zero runtime bytecode manipulation, 100% native image compatible.

---

## ADR-008: Relationship Loading Strategy & N+1 Prevention

### Context
How should nested relationships (`student { department { name } courses { title } }`) be loaded without causing N+1 database queries?

### Options Considered
1. **Mega-Joins in a Single Query**:
   - Join all related tables in one massive SQL query.
   - *Pros*: 1 SQL query.
   - *Cons*: Cartesian product explosion for multiple 1:N relations (e.g. 1 student * 10 courses * 5 clubs = 50 duplicate rows); massive data transfer over the wire; complex result grouping.
2. **Naive Lazy Loading (N+1)**:
   - Execute a query for each parent row.
   - *Pros*: Simple.
   - *Cons*: Catastrophic performance failure for lists (100 students = 101 queries).
3. **Batched Loading via GraphQL DataLoader (`WHERE id IN (?, ...)` )**:
   - Fetch root items in 1 query; collect foreign keys; fetch all related items in 1 batched query; group in memory.
   - *Pros*: Exactly 2 queries for 1:N (1 for parents + 1 for children), zero Cartesian explosion, optimal database execution.
   - *Cons*: Requires managing DataLoader batch keys and execution lifecycle.

### Recommended Choice
**Option 3: Batched Relationship Loading via GraphQL DataLoader (`WHERE id IN (?)`)**.

### Consequences
- Zero N+1 query regression.
- Tested and asserted via SQL statement count tracking in integration tests.

---

## ADR-009: DataLoader Integration & Hidden Dependency Injection

### Context
When a client requests a relation (e.g. `student { department { name } }`) but does NOT request the foreign key (`departmentId`), how does the DataLoader obtain the key needed for batching?

### Options Considered
1. **Always SELECT * from database**:
   - *Pros*: Foreign key is always present.
   - *Cons*: Defeats selection-set optimization; fetches sensitive/unneeded columns.
2. **Hidden Dependency Column Injection**:
   - The query planner inspects downstream relations requested in the GraphQL selection set, identifies foreign key dependencies, and injects them into the SQL query marked with `isHiddenDependency = true`.
   - *Pros*: Minimal columns fetched; downstream DataLoader gets required keys; hidden keys are stripped before returning the final response.
   - *Cons*: Query planner must trace field dependencies.

### Recommended Choice
**Option 2: Hidden Dependency Column Injection**.

### Consequences
- Optimal SQL projections with seamless relationship resolution.
- Hidden keys are automatically excluded from the final GraphQL payload.

---

## ADR-010: Two-Tiered View Model Strategy (DB-Backed & GraphORM-Defined)

### Context
How should read-only aggregated and joined views be represented and queried?

### Options Considered
1. **Database-Backed Views Only (`@@view`)**:
   - Developers must create SQL views or materialized views in PostgreSQL via DDL scripts.
   - *Pros*: Query engine treats views identically to tables.
   - *Cons*: Requires DBA intervention or migration scripts for every minor UI projection.
2. **Virtual / GraphORM-Defined Views Only**:
   - Views defined only in `schema.graphorm` with `@@from`, `@@leftJoin`, and `@@groupBy`.
   - *Pros*: Zero database DDL changes needed.
   - *Cons*: Cannot leverage pre-materialized PostgreSQL views or existing enterprise database views.
3. **Two-Tiered Strategy (Support Both DB-Backed and GraphORM-Defined Views)**:
   - *Pros*: Maximum developer flexibility. DB-backed views for materialized/pre-existing views; GraphORM-defined views for rapid application projections.
   - *Cons*: Query planner must support both table-like views and dynamic join-tree planning.

### Recommended Choice
**Option 3: Two-Tiered Strategy Supporting Both View Types**.

### Consequences
- Unified query engine executes direct SELECT for DB views and dynamic multi-table joins for GraphORM-defined views.
- Strict read-only invariant enforced across both tiers.

---

## ADR-011: SQL Dialect Abstraction & PostgreSQL Target

### Context
How should database-specific SQL features (quoting, pagination, aggregate functions, returning clauses) be handled?

### Options Considered
1. **Hardcoded PostgreSQL SQL**:
   - *Pros*: Simple, fast initial implementation.
   - *Cons*: Impossible to support MySQL, MariaDB, or H2 in the future without major rewrites.
2. **Pluggable `SqlDialect` Interface with Initial `PostgreSqlDialect`**:
   - *Pros*: Clean separation of dialect concerns; allows mock/in-memory dialect testing; enables MySQL/H2 expansion.
   - *Cons*: Slight abstraction overhead.

### Recommended Choice
**Option 2: Pluggable `SqlDialect` Interface with Initial `PostgreSqlDialect`**.

### Consequences
- PostgreSQL 15+ is the first-class, fully tested implementation.
- Dialect encapsulates identifier quoting (`"`), pagination (`LIMIT ? OFFSET ?`), RETURNING clauses, and aggregation syntax.

---

## ADR-012: Code Generation Strategy (JavaPoet Compile-Time Generation)

### Context
How should Java models, view records, inputs, and client APIs be generated from `schema.graphorm`?

### Options Considered
1. **Runtime Reflection & Dynamic Proxies**:
   - *Pros*: No code generation step.
   - *Cons*: No compile-time type safety; slow; incompatible with IDE auto-completion and GraalVM.
2. **FreeMarker / Velocity String Templates**:
   - *Pros*: Flexible text formatting.
   - *Cons*: Error-prone, hard to refactor, no Java syntax checking during generation.
3. **JavaPoet Code Generation Engine**:
   - *Pros*: Strongly-typed Java code generation API, automatic imports management, indentation, verified syntax, robust community standard.
   - *Cons*: Requires learning JavaPoet API.

### Recommended Choice
**Option 3: JavaPoet Engine in `graphorm-generator`**.

### Consequences
- Clean, idiomatic Java 21 records and fluent builder classes.
- Verified in tests by compiling generated code with `javax.tools.JavaCompiler`.

---

## ADR-013: Spring Boot Auto-Configuration & Modular Independence

### Context
How should GraphORM integrate with Spring Boot applications while keeping core modules independent?

### Options Considered
1. **Direct Spring Dependencies in Core Modules**:
   - *Pros*: Fast integration.
   - *Cons*: Makes GraphORM unusable in Quarkus, Micronaut, CLI tools, or vanilla Java. Violates Rule 6 in `AGENTS.md`.
2. **Strict Modular Decoupling with `graphorm-spring-boot-starter`**:
   - `graphorm-core`, `graphorm-schema`, `graphorm-generator`, and `graphorm-jdbc` have zero Spring dependencies.
   - `graphorm-spring-boot-starter` provides `@AutoConfiguration`, Spring DataSource binding, and Spring for GraphQL integration.
   - *Pros*: Maximum modularity, clean architecture, testable in isolation.
   - *Cons*: Requires careful dependency boundary enforcement.

### Recommended Choice
**Option 2: Strict Modular Decoupling with Dedicated Spring Boot Starter**.

### Consequences
- Core modules remain lightweight and portable.
- Spring Boot applications get zero-friction starter integration.

---

## ADR-014: Transaction Management & Spring PlatformTransactionManager

### Context
How should database transactions be managed across standalone JDBC and Spring Boot environments?

### Options Considered
1. **ThreadLocal Connection Management**:
   - *Pros*: Works in pure JDBC.
   - *Cons*: Conflicts with Spring's `@Transactional` and Virtual Threads.
2. **Pluggable `TransactionManager` SPI with Spring Synchronization**:
   - `graphorm-jdbc` provides a lightweight `TransactionManager` interface and default JDBC implementation using `Connection.setAutoCommit(false)`.
   - `graphorm-spring-boot-starter` implements the SPI by delegating to Spring's `DataSourceUtils` and `PlatformTransactionManager`.
   - *Pros*: Seamless support for Spring's `@Transactional` alongside pure JDBC fallback.
   - *Cons*: Must handle connection lifecycle hooks carefully.

### Recommended Choice
**Option 2: Pluggable `TransactionManager` SPI with Spring `DataSourceUtils` Adapter**.

### Consequences
- Pure JDBC works standalone in CLI and tests.
- Spring Boot applications participate transparently in Spring transaction boundaries.

---

## ADR-015: Error Handling Hierarchy & Field-Level Authorization

### Context
How should system errors and security access violations be structured and reported to clients?

### Options Considered
1. **Generic RuntimeExceptions with String Messages**:
   - *Pros*: Quick.
   - *Cons*: Unactionable errors, potential information leaks, cannot be handled programmatically.
2. **Domain Exception Hierarchy with Sanitized Client Mapping**:
   - Root: `GraphOrmException`.
   - Subtypes: `SchemaValidationException`, `QueryPlanningException`, `ReadOnlyViewException`, `AccessDeniedException`, `RecordNotFoundException`.
   - Interceptors map domain exceptions to standard GraphQL error extensions with error codes.
   - Raw database errors are logged internally but masked from API responses.
   - *Pros*: Production security, clean diagnostics, developer-friendly debugging.
   - *Cons*: Requires explicit exception mapping infrastructure.

### Recommended Choice
**Option 2: Strongly Typed Domain Exception Hierarchy with Client-Safe Error Sanitization**.

### Consequences
- High security standards preventing database schema or credential leaks.
- Clear, actionable diagnostics with source coordinates (file, line, column) for developers.
