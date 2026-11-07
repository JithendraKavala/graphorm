# GraphORM: Architectural Decision Records (ADRs)

This document captures the key architectural decisions, rationale, trade-offs, and invariants governing GraphORM design and implementation.

---

## ADR-001: Schema-First Architecture Inspired by Prisma

### Context
GraphORM requires a schema declaration language that is readable, expressive, and capable of modeling regular models, view models, relationships, and composite types.

### Options Considered
1. **Java Annotations Only (JPA-Style)**:
   - *Pros*: Familiar to traditional Spring developers.
   - *Cons*: Cannot easily generate schema documentation; hard to build unified language tooling; verbose; tightly couples schema definitions to Java bytecode.
2. **GraphQL SDL Only**:
   - *Pros*: Native GraphQL schema alignment.
   - *Cons*: Missing relational mapping semantics (table names, column mappings, indexes, joins).
3. **Custom Prisma-Inspired Schema Language (`schema.graphorm`)**:
   - *Pros*: Human-readable, declarative, concise; native support for directives (`@id`, `@map`, `@relation`, `@@from`, `@@leftJoin`, `@@groupBy`); clean separation of schema design from runtime code.
   - *Cons*: Requires building an ANTLR4 parser and validator.

### Recommended Choice
**Option 3: Custom Prisma-Inspired Schema Language (`schema.graphorm`)**.

### Consequences
- Single source of truth for database mapping, Java code generation, and GraphQL schema derivation.
- ANTLR4-based parser grammar provides robust AST construction and error diagnostics.

---

## ADR-002: Model Categories (Regular Models vs View Models vs Composite Types)

### Context
Applications require both transactional read-write entities, complex read-only analytical/dashboard projections, and reusable value objects.

### Options Considered
1. **Homogeneous Entities (Everything is a `model`)**:
   - *Pros*: Single entity concept.
   - *Cons*: Blurs read-only vs writable boundaries; permits accidental mutations on view projections; creates confusion around primary keys and joins.
2. **Explicit Strict Tripartite Categorization (`model`, `view`, `type`)**:
   - `model`: Physical database table with full CRUD capabilities.
   - `view`: Read-only projection combining multiple models, joins, aggregations, and computed expressions.
   - `type`: Reusable nested value object without independent database identity.
   - *Pros*: Strict compile-time and runtime invariants; impossible to generate write mutations for views; clear mental model.
   - *Cons*: Requires separate AST nodes and validation rules for each category.

### Recommended Choice
**Option 2: Explicit Strict Tripartite Categorization (`model`, `view`, `type`)**.

### Consequences
- View models are strictly immutable and read-only.
- The schema validator and code generator enforce zero write mutations on views.

---

## ADR-003: Strict Read-Only Invariant on View Models

### Context
View models represent projections, calculations, and aggregations across one or more tables. How should write operations be handled?

### Invariant
**View models are 100% read-only across all layers.**
- Schema Validator: Rejects `@default(autoincrement())` or mutable attributes on view fields.
- Code Generator: Omits `create`, `update`, and `delete` input types and mutation APIs for views.
- GraphQL Engine: Registers only Query fields (`studentSummary`, `studentSummaries`), never Mutation fields.
- JDBC Engine: Throws `ReadOnlyViewException` if targeted by insert, update, or delete builders.

---

## ADR-004: Pushdown Query Execution (Zero In-Memory Table Dumps)

### Context
When querying a view model that aggregates or joins multiple tables, where should filtering, pagination, and sorting happen?

### Options Considered
1. **In-Memory Join & Filtering (Naive ORM)**:
   - Query all rows from source tables into JVM memory, execute joins and filters using Java streams.
   - *Pros*: Easy to implement initially.
   - *Cons*: Catastrophic performance failure on large datasets; massive memory footprint; OOM risk; violates enterprise production requirements.
2. **Database Pushdown (Single Dynamic SQL Execution)**:
   - Compile view projections, joins, aggregations, filters, sorting, and pagination into a single parameterized SQL query executed directly in PostgreSQL.
   - *Pros*: Leverages PostgreSQL query optimizer, indexes, and execution engine; minimal data over the wire; sub-millisecond execution.
   - *Cons*: Requires a sophisticated SQL dialect generator and query planner.

### Recommended Choice
**Option 2: Database Pushdown via Unified Query AST**.

### Consequences
- Strictly enforced throughout `graphorm-jdbc` and verified via Testcontainers integration tests.
- GraphORM never dumps tables into JVM memory.

---

## ADR-005: Unified Query AST for Regular Models and View Models

### Context
Should view models use a separate query engine or query AST from regular models?

### Options Considered
1. **Separate Query Engines**:
   - Different pipelines for regular models vs view models.
   - *Pros*: Independent implementations.
   - *Cons*: Massive code duplication, divergent feature sets, conflicting pagination/sorting logic.
2. **Unified `SelectQuery` AST**:
   - A single, strongly typed `SelectQuery` AST that represents both single-table model queries and complex multi-table view queries with joins, aggregations, and GROUP BY.
   - *Pros*: Reusable query building, consistent SQL dialect generation, uniform parameter extraction.
   - *Cons*: The AST must support both simple and complex query structures.

### Recommended Choice
**Option 2: Unified `SelectQuery` AST**.

### Consequences
- `SelectBuilder` handles both regular models and view models through the same parameterized generation pipeline.
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
   - `graphorm-core`, `graphorm-schema`, `graphorm-generator`, and `graphorm-jdbc` have zero Spring dependencies.\n   - `graphorm-spring-boot-starter` provides `@AutoConfiguration`, Spring DataSource binding, and Spring for GraphQL integration.
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
   - *Pros*: Seamless support for Spring's `@Transactional` alongside pure JDBC fallback.\n   - *Cons*: Must handle connection lifecycle hooks carefully.

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

---

## ADR-016: Raw SQL Subquery View Models (`@@query` for UNION, CTEs, and Complex Projections)

### Context
Certain critical database views require SQL language features—such as `UNION`, `UNION ALL`, Common Table Expressions (`WITH`), and Window Functions—that cannot be expressed cleanly through declarative joins (`@@from`, `@@leftJoin`). How should GraphORM support these complex read-only views while preserving type-safety, automatic GraphQL schema generation, and query pushdown?

### Options Considered
1. **Force Database DDL Views Only (`@@view`)**:
   - Users must create a physical database view or migration in PostgreSQL for every `UNION` query.
   - *Pros*: Engine queries a named relation.
   - *Cons*: Inconvenient for developer velocity; tightly couples schema definitions to database migration pipelines.
2. **Ad-Hoc Unparameterized Raw SQL Strings in Client Code**:
   - Users write raw SQL strings directly in Java repository methods.
   - *Pros*: Quick for one-off queries.
   - *Cons*: Bypasses GraphORM's schema, type generator, GraphQL wiring, and security validators; high risk of SQL injection.
3. **Schema-Defined Raw SQL Views (`@@query`) Wrapped in Derived Subqueries**:
   - Declare the subquery in the schema using single-line `@@query("...")` or multiline `@@query("""...""")`.
   - Enforce compile-time security validation (must begin with `SELECT` or `WITH`, no DDL/DML keywords).
   - The JDBC query planner wraps the raw query in a derived table `FROM (<raw_query>) AS <alias>`, applying pushdown filters (`WHERE`), ordering (`ORDER BY`), and pagination (`LIMIT`, `OFFSET`) with 100% positional parameter extraction (`?`).
   - Generate read-only Java model records, client queries, and GraphQL Query fields identically to other view models.

### Recommended Choice
**Option 3: Schema-Defined Raw SQL Views (`@@query`) with Derived Subquery Pushdown**.

### Consequences
- Unlocks full SQL expressive power (including `UNION`, `UNION ALL`, recursive/non-recursive CTEs, and window functions).
- Retains 100% type-safety, automatic GraphQL exposure, and strict read-only enforcement.
- Safe from SQL injection via outer pushdown parameterization.
