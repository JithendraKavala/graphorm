# GraphORM: System Architecture & Data Flow

## 1. Architectural Philosophy

GraphORM is built on four core architectural principles:

1. **Hybrid Schema-First Architecture**: Database models (`model`), read-only views (`view`), and composite structures (`type`) are defined in a Prisma-inspired schema DSL (`schema.graphorm`). Public APIs are defined in standard GraphQL SDL (`schema.graphqls`). A mapping layer reconciles the two, allowing selective exposure of database fields and view projections.
2. **Unified Query Engine & Direct JDBC Execution**: Avoid JPA/Hibernate overhead, session state, and unpredictable fetching. GraphORM uses direct, parameterized JDBC with database-specific dialects (`SqlDialect`). Regular models and view models are unified under a single query AST and execution pipeline.
3. **Selection-Aware Execution**: The GraphQL execution engine feeds field selection trees into the query planner, which translates them directly into SQL `SELECT` projections with batched relationship loaders, eliminating over-fetching.
4. **First-Class View Model Integration**: Rather than treating multi-table projections or aggregations as ad-hoc DTOs or secondary queries, views are first-class schema entities. The query planner natively resolves view source dependencies, joins, and aggregations, pushing execution down to the database.

## 2. End-to-End Data Flow

### A. Regular Model Query Flow
```mermaid
sequenceDiagram
    autonumber
    actor Client as GraphQL Client
    participant HTTP as Spring WebMvc / WebFlux
    participant GQL as Spring for GraphQL (GraphQL Java)
    participant DF as GraphORM DataFetcher
    participant QP as Query Planner
    participant DL as DataLoader Registry
    participant JDBC as GraphORM JDBC Engine
    participant DB as PostgreSQL Database

    Client->>HTTP: POST /graphql (query { students { id name department { name } } })
    HTTP->>GQL: Execute GraphQL Request
    GQL->>DF: Invoke Registered Student DataFetcher
    DF->>QP: Analyze SelectionSet (students: [id, name, department])
    QP->>QP: Identify Direct Columns (id, name)
    QP->>QP: Inject Dependency Columns (department_id)
    QP->>JDBC: Build & Execute Projected SQL
    JDBC->>DB: SELECT id, name, department_id FROM students
    DB-->>JDBC: ResultSet
    JDBC-->>DF: Map to Student Record List
    DF-->>GQL: Return Student List to GraphQL Engine

    Note over GQL,DL: Resolving Nested Field: department
    GQL->>DF: Invoke Department Relation DataFetcher
    DF->>DL: Queue department_id in DepartmentDataLoader
    DL->>JDBC: Batch Load: SELECT id, name FROM departments WHERE id IN (?)
    JDBC->>DB: Execute Batched Query
    DB-->>JDBC: ResultSet
    JDBC-->>DL: Map to Department Records
    DL-->>GQL: Resolve Department for Each Student
    GQL-->>HTTP: Serialized JSON Response
    HTTP-->>Client: 200 OK Response
```

### B. View Model Query Flow (GraphORM-Defined / DB-Backed)
```mermaid
sequenceDiagram
    autonumber
    actor Client as GraphQL Client
    participant HTTP as Spring WebMvc / WebFlux
    participant GQL as Spring for GraphQL (GraphQL Java)
    participant DF as View DataFetcher (Read-Only)
    participant QP as Unified Query Planner
    participant JDBC as GraphORM JDBC Engine
    participant DB as PostgreSQL Database

    Client->>HTTP: POST /graphql (query { studentDashboard { studentName departmentName courseCount } })
    HTTP->>GQL: Execute GraphQL Request
    GQL->>DF: Invoke Registered View DataFetcher
    DF->>QP: Plan View Query (studentDashboard SelectionSet)
    QP->>QP: Resolve View Source Models & Join Dependencies
    QP->>QP: Prune Unselected Columns & Build Aggregations (COUNT)
    QP->>JDBC: Generate Parameterized SQL with Joins/GROUP BY
    JDBC->>DB: SELECT s.name AS studentName, d.name AS departmentName, COUNT(e.id) AS courseCount FROM students s LEFT JOIN departments d ON s.dept_id = d.id LEFT JOIN enrollments e ON s.id = e.student_id GROUP BY s.name, d.name
    DB-->>JDBC: ResultSet
    JDBC-->>DF: Map to StudentDashboard Record List
    DF-->>GQL: Return View Data to GraphQL Engine
    GQL-->>HTTP: Serialized JSON Response
    HTTP-->>Client: 200 OK Response
```

## 3. Component Architecture

### A. Schema & Metadata Engine (`graphorm-core`, `graphorm-schema`)
- Parses `schema.graphorm` into an immutable `SchemaAST`.
- Distinguishes three core model categories:
  - **Regular Models (`ModelNode`)**: Physical tables, CRUD-capable.
  - **View Models (`ViewModelNode`)**: Read-only projections, joins, aggregations, database-backed (`@@view`, `@@map`) or GraphORM-defined (`from: Model`).
  - **Composite Types (`CompositeTypeNode`)**: Reusable nested data structures.
- Validates field types, scalar declarations, primary keys, view dependencies, expressions, and bidirectional relations.
- Produces an in-memory `ModelRegistry` containing `ModelMetadata`, `ViewModelMetadata`, `FieldMetadata`, `RelationMetadata`, and `CompositeTypeMetadata`.

### B. Unified Query Planner & Optimizer (`graphorm-graphql`, `graphorm-core`)
- Intercepts `DataFetchingEnvironment`.
- Inspects `DataFetchingFieldSelectionSet` to build an execution `QueryPlan`.
- Supports both regular model queries and complex view model queries within a unified planning abstraction.
- For view queries:
  - Resolves dependencies between source models and nested views.
  - Generates join and aggregation plans.
  - Computes minimal column projections while auto-injecting hidden foreign keys or join dependency columns.
  - Eliminates N+1 queries when navigating nested relations from or to views.

### C. JDBC Database Engine (`graphorm-jdbc`)
- Translates `SelectQuery`, `ViewModelQueryPlan`, `InsertQuery`, `UpdateQuery`, and `DeleteQuery` into dialect-specific parameterized SQL (`BoundSql`).
- Strictly enforces read-only execution on view models (mutations rejected at compile-time and runtime).
- Supports database-backed views (direct select against views/materialized views) and dynamic GraphORM-defined views (subqueries, CTEs, joins, and `GROUP BY` expressions).
- Manages connections, transactions, and execution.
- Maps `ResultSet` rows directly into immutable Java records or view DTOs via `DynamicRowMapper`.

### D. Spring Boot Starter (`graphorm-spring-boot-starter`)
- Auto-configures beans upon detecting `schema.graphorm` and a `DataSource`.
- Wires DataFetchers into GraphQL Java `GraphQLCodeRegistry`:
  - Registers Query & Mutation fetchers for regular models.
  - Registers **Query-only** fetchers for view models.
  - Ensures mutations are never registered for view models.
- Integrates transaction handling with Spring's `PlatformTransactionManager`.
