# GraphORM

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![GraalVM Native](https://img.shields.io/badge/GraalVM-Native%20Image%20Ready-blueviolet.svg)](https://www.graalvm.org/)
[![GraphQL Java](https://img.shields.io/badge/GraphQL%20Java-21+-e10098.svg)](https://www.graphql-java.com//)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15%2B-336791.svg)](https://www.postgresql.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8%2B-4479A1.svg)](https://www.mysql.com/)
[![SQLite](https://img.shields.io/badge/SQLite-3.x-003B57.svg)](https://www.sqlite.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-success.svg)](https://github.com/graphorm/graphorm/actions)
[![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-success.svg)](https://github.com/graphorm/graphorm/actions)

**GraphORM** is a modern, high-performance **Hybrid Schema-First ORM and Automatic GraphQL Engine** designed specifically for **Java 21+**, **Spring Boot 3+**, and **GraalVM Native Image (AOT)**.

GraphORM eliminates the traditional object-relational impedance mismatch by treating schemas as the single source of truth, compiling queries into minimal, parameterized SQL ASTs without ORM entity caching or dirty-tracking overhead. It runs on **PostgreSQL 15+**, **MySQL 8+**, and **SQLite** via a first-class multi-dialect SPI.

---

## 🚀 Key Features

- **Schema-First Data Modeling**: Prisma-inspired declarative DSL (`schema.graphorm`) supporting models, physical views, virtual views, composite types, enums, check constraints, and foreign key relations.
- **Dedicated Maven Build Integration (`graphorm-maven-plugin`)**: Seamless code generation during `mvn compile` / `mvn generate-sources` with `add-source` support and configuration parameters.
- **GraalVM Native-Image (AOT) Ready**: Sub-50ms cold starts, <60MB RSS memory footprint, and automated reachability metadata (`GraphOrmRuntimeHintsRegistrar`).
- **First-Class View Models**:
  - *Stage 1 (Database-Backed)*: Direct read-only mapping to PostgreSQL physical views (`@@view`, `@@map`).
  - *Stage 2 (Virtual Views)*: Dynamic schema-defined views with multi-table joins (`@@from`, `@@leftJoin`, `@@innerJoin`), aggregations (`@aggregate(count/sum/avg)`), expressions (`@expression`), and group-by columns (`@@groupBy`).
  - *Stage 3 (Raw SQL Subquery Views)*: Support for `UNION`, window functions, and CTEs via `@@query("...")`.
  - *Join Pruning Optimization*: Automatically prunes unselected table joins from generated SQL when fields are not requested.
- **Automatic Spring for GraphQL Wiring**:
  - Zero-code DataFetchers for queries, mutations, and Relay-style cursor connections.
  - Automatic relationship batching via GraphQL Java DataLoaders (eliminates N+1 queries).
  - Selection-set projection pruning (requests only client-specified SQL columns).
  - Strict read-only invariant enforcement (views never expose mutations).
- **Type-Safe Java 21 Client API**:
  - Code generator powered by JavaPoet emitting immutable Java 21 records with builders and `.with*()` methods.
  - Fluent, type-safe criteria filter builders.
  - Prisma-style client (`db.student()`, `db.department()`, `db.studentDashboard()`).
  - Optimistic locking with `@version` auto-incrementing.
- **Database Migrations (`graphorm migrate`)**:
  - Automated diffing between `schema.graphorm` and live PostgreSQL catalog.
  - Distributed transaction-level advisory locks (`pg_advisory_xact_lock`) and SHA-256 audit ledger.
  - Destructive change detection and `--force` safeguards.
- **Multi-Dialect Support (Phase 21)** — *New*:
  - `QueryExecutionEngine` SPI with blocking and async (`CompletableFuture`) execution paths.
  - **PostgreSQL 15+**: `RETURNING` clause, advisory locks, full-text search, `pg_advisory_xact_lock`.
  - **MySQL 8+**: `MySqlDialect` with backtick quoting, `LIMIT ? OFFSET ?`, `ON CONFLICT` → `ON DUPLICATE KEY UPDATE`, MySQL error code translation (1062, 1451/1452, 3819/4025).
  - **SQLite**: `SqliteDialect` with `INTEGER PRIMARY KEY AUTOINCREMENT`, boolean-integer coercion, `LIMIT -1 OFFSET ?` pagination, in-memory integration tests via `SqliteInMemoryCrudIntegrationTest`.
  - Automatic JDBC metadata dialect auto-detection (`SqlDialect.fromDatabaseProductName`).
  - Project Loom virtual-thread safety via `ReentrantLock` replacement of `synchronized` blocks.
- **Native First-Class Multi-Tenancy (Phase 22)** — *New*:
  - `@tenantId` field directive and `@@tenant(field)` model directive in schema DSL.
  - Thread-local `TenantContext` / `TenantContextProvider` SPI with `SpringTenantContextProvider` bridging Spring Security and HTTP headers.
  - Transparent predicate pushdown across all query builders (`SelectBuilder`, `InsertBuilder`, `UpdateBuilder`, `DeleteBuilder`, `ViewQueryPlanner`) — no application code changes required.
  - Cross-tenant DataLoader partitioning via `TenantPartitionKey` in `BatchRelationLoader`.
  - `TenantHeaderFilter` and `graphorm.tenant.*` auto-configuration properties in Spring Boot starter.
  - `TenantContextMissingException` mapped to HTTP 403 in GraphQL error handler.
  - SQLite in-memory data-isolation integration tests (`SqliteMultiTenancyIntegrationTest`).
- **Multi-Tier Caching Engine (Phase 23)** — *New*:
  - **L1 Plan Cache**: In-memory AST-level query plan cache for repeated identical selection sets.
  - **L2 Distributed View Cache**: `@cached(ttl = 60, tags = ["ModelName"])` annotation on view models backed by Caffeine.
  - Automatic tag-based cache eviction — any mutation on a tagged source model purges all matching L2 entries.
  - `GraphOrmCacheManager` and `CacheEvictionListener` wired via Spring Boot auto-configuration.
  - `graphorm.cache.*` properties for TTL, maximum size, and eviction strategy configuration.
- **Real-Time Subscriptions via LISTEN/NOTIFY (Phase 24)** — *New*:
  - `@@notify(on: [CREATE, UPDATE, DELETE])` schema directive enables PostgreSQL LISTEN/NOTIFY for any model.
  - `PostgresNotificationListener` background daemon maintains a dedicated JDBC connection and routes events to the `EntityEventBroker` SPI.
  - `EntityEventBroker` (in-memory default) and `EntityEvent` / `EntityEventCodec` core SPI.
  - `ModelSubscriptionDataFetcher` registered as GraphQL `Subscription` root — standard WebSocket/SSE transport.
  - Zero-leak multi-tenant event filtering: subscription events are scoped to the requesting tenant's partition.
  - Spring Boot `graphorm.subscriptions.*` auto-configuration; `GraphQlSdlGenerator` auto-emits Subscription SDL.
- **Production-Ready Security & DoS Defense**:
  - Field-level and entity-level authorization using `@auth(roles: [...])` backed by Spring Security.
  - Backend-injected tamper-proof attributes (`@injected(from: "...")`).
  - GraphQL query depth and complexity limit instrumentations.
  - 100% SQL parameterization and portable error translation.
  - Security milestone audit: null-safety, `@auth` edge cases, dialect consistency, and locking hardening.
- **Developer CLI Tooling (`graphorm-cli`)**:
  - `graphorm init`: Scaffolds starter projects.
  - `graphorm validate`: Validates schema syntax and semantic constraints with line/column caret diagnostics.
  - `graphorm generate`: Generates Java 21 records and client code with `--check` verification mode.
  - `graphorm introspect`: Reverse-engineers live PostgreSQL databases into `schema.graphorm`.
  - `graphorm fmt` & `graphorm lint`: Canonical schema formatting and diagnostic linting.
- **Built-in Observability**:
  - Micrometer performance metrics (`graphorm.query.duration`, `graphorm.query.count`, `graphorm.dataloader.batch.size`).
  - SQL query fingerprinting preserving PII privacy.
  - PostgreSQL plan explainability hook (`explainPlan`).

---

## 🏛️ System Architecture

```
                          +-------------------------+
                          |     schema.graphorm     |
                          +------------+------------+
                                       |
                +----------------------+----------------------+
                |                                             |
                v                                             v
     +--------------------+                        +--------------------+
     |  graphorm-schema   |                        | graphorm-generator |
     |   ANTLR4 Parser    |                        | JavaPoet Codegen   |
     +----------+---------+                        +----------+---------+
                |                                             |
                v                                             v
     +--------------------+                        +--------------------+
     |   ModelRegistry    |                        | Immutable Records  |
     | (Immutable Schema) |                        | & Type-Safe Client |
     +----------+---------+                        +----------+---------+
                |                                             |
                +----------------------+----------------------+
                                       |
                                       v
     +------------------------------------------------------------------+
     |                     QueryExecutionEngine SPI                     |
     |          (Blocking + CompletableFuture async dispatch)           |
     +---------------------------------+--------------------------------+
                                       |
                      +----------------+------------------+
                      |                |                  |
                      v                v                  v
            +------------------+ +-----------+ +------------------+
            | PostgreSqlDialect| | MySqlDialect| | SqliteDialect  |
            | (RETURNING, LISTEN| |(backticks, | |(AUTOINCREMENT, |
            |  /NOTIFY, Locks) | | ON DUPL.)  | | bool-to-int)   |
            +--------+---------+ +-----------+ +------------------+
                     |
                     v
     +------------------------------------------------------------------+
     |                          graphorm-jdbc                           |
     |   Unified Query AST, Multi-Dialect RowMapper, Migrations,       |
     |   TenantContext Pushdown, L1 Plan Cache, LISTEN/NOTIFY Listener  |
     +---------------------------------+--------------------------------+
                                       |
               +-----------------------+------------------------+
               |                       |                        |
               v                       v                        v
  +------------------+    +---------------------+    +--------------------+
  | EntityEventBroker|    |  GraphOrmCacheManager|    |  TenantContext    |
  | LISTEN/NOTIFY SPI|    |  L1 Plan + L2 Caffeine|   | ThreadLocal / SPI |
  | EntityEvent Codec|    |  Tag-Based Eviction  |    | SpringProvider    |
  +--------+---------+    +----------+----------+    +--------+----------+
           |                         |                        |
           +-------------+-----------+------------------------+
                         |
                         v
     +------------------------------------------------------------------+
     |                         graphorm-graphql                         |
     |   Generic DataFetchers, SelectionSet Pruning, DataLoader Batch,  |
     |   ModelSubscriptionDataFetcher, Field-Level @auth Authorization  |
     +---------------------------------+--------------------------------+
                                       |
                                       v
     +------------------------------------------------------------------+
     |                   graphorm-spring-boot-starter                   |
     |   Auto-Configuration, DataSource, @Transactional, GraalVM AOT,  |
     |   TenantHeaderFilter, Cache/Subscription/Tenant Properties       |
     +------------------------------------------------------------------+
```

---

## ⚡ 60-Second Example

### 1. Define Schema (`src/main/resources/schema.graphorm`)
```prisma
datasource db {
  provider = "postgresql"
  url      = "postgresql://localhost:5432/university_db"
}

generator client {
  provider = "graphorm-client-java"
  package  = "com.example.university"
}

model Department {
  id       Int       @id @default(autoincrement())
  name     String    @map("dept_name")
  code     String    @unique
  tenantId String    @tenantId @map("tenant_id")  // 🆕 Multi-tenancy
  students Student[]

  @@map("departments")
  @@tenant(field: "tenantId")                       // 🆕 Partition by tenant
  @@notify(on: [CREATE, UPDATE, DELETE])             // 🆕 Real-time events
}

model Student {
  id           Int         @id @default(autoincrement())
  name         String
  email        String      @unique
  gpa          Float?
  version      Int         @default(1) @version
  tenantId     String      @tenantId @map("tenant_id")  // 🆕 Multi-tenancy
  departmentId Int         @map("department_id")
  department   Department? @relation(fields: [departmentId], references: [id])

  @@map("students")
  @@index([departmentId], name: "idx_students_department")
  @@tenant(field: "tenantId")
  @@notify(on: [CREATE, UPDATE, DELETE])
}

// 🆕 L2 cached view model with tag-based eviction
view StudentDashboard @cached(ttl = 60, tags = ["Student", "Department"]) {
  studentId      Int     @map("s.id")
  studentName    String  @map("s.name")
  departmentName String? @map("d.dept_name")

  @@from(Student, as: "s")
  @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
  @@groupBy(["s.id", "s.name", "d.dept_name"])
}
```

### 2. Query via Type-Safe Java 21 Client
```java
@Service
public class StudentService {
    @Autowired
    private GraphOrmClient db;

    public List<Student> getHonorStudents() {
        return db.student().findMany(q -> q
            .where(f -> f.departmentId().eq(1).and(f.gpa().gte(3.5)))
            .orderBy(f -> f.name().asc())
            .limit(20)
        );
    }

    // 🆕 Upsert by unique key (ON CONFLICT DO UPDATE)
    public Student enrollOrUpdate(CreateStudentInput input) {
        return db.student().upsert(input, "email");
    }
}
```

### 3. Query via GraphQL (GraphiQL at `/graphiql`)
```graphql
query {
  students {
    id
    name
    department {
      name
    }
  }
  studentDashboards {
    studentId
    studentName
    departmentName
  }
}

# 🆕 Real-time subscription (WebSocket transport)
subscription {
  studentCreated {
    id
    name
    email
  }
}
```

---

## 📦 Multi-Module Organization

| Module | Description | Dependency Invariant |
|---|---|---|
| [`graphorm-core`](graphorm-core) | Metadata, AST models, query builders, `QueryExecutionEngine` SPI, `EntityEventBroker`, `TenantContext`, portable exceptions | Zero DB/Spring dependencies |
| [`graphorm-schema`](graphorm-schema) | ANTLR4 grammar, AST visitor, validator, transformer; supports `@tenantId`, `@@tenant`, `@@notify`, `@cached` | Core + ANTLR4 runtime only |
| [`graphorm-generator`](graphorm-generator) | JavaPoet generator for records, DTOs, and client APIs | Core + JavaPoet + JDBC |
| [`graphorm-jdbc`](graphorm-jdbc) | Multi-dialect SQL generation (PostgreSQL/MySQL/SQLite), execution, row mapping, transactions, migrations, `PostgresNotificationListener`, `TenantContext` pushdown, L1 plan cache | Core + `java.sql` only (No Spring) |
| [`graphorm-graphql`](graphorm-graphql) | Auto DataFetchers, DataLoaders, `ModelSubscriptionDataFetcher`, selection set analyzer, complexity limits, `@auth` field-level RBAC | Core + JDBC + GraphQL Java |
| [`graphorm-spring-boot-starter`](graphorm-spring-boot-starter) | Spring Boot 3 auto-configuration, security authorizers, GraalVM AOT hints, `TenantHeaderFilter`, `GraphOrmCacheManager`, subscription and cache properties | Spring Boot 3 + GraphQL + JDBC |
| [`graphorm-cli`](graphorm-cli) | Picocli developer CLI (`validate`, `generate`, `init`, `introspect`, `migrate`, `fmt`, `lint`) | Core + Schema + Generator + JDBC |
| [`graphorm-maven-plugin`](graphorm-maven-plugin) | Maven build plugin for automated build-time code generation | Core + Schema + Generator + Maven Plugin API |
| [`graphorm-examples`](graphorm-examples) | End-to-end reference applications (`spring-boot-postgres`, `university-dashboard`) with Live Feature Lab showcasing all 24 phases | Full stack demonstration (Spring Boot + PostgreSQL + React 18) |


---

## 📚 Documentation

- [Quickstart Guide (5-Minute Tutorial)](docs/QUICKSTART.md)
- [Schema Specification & Reference](docs/SCHEMA_REFERENCE.md)
- [Migration & CLI Guide](docs/MIGRATION_GUIDE.md)
- [Production Deployment & GraalVM Native-Image Guide](docs/PRODUCTION_DEPLOYMENT.md)
- [Developer Guide (Architecture & Patterns)](docs/DEVELOPER_GUIDE.md)
- [CLI Tool Reference](docs/CLI_REFERENCE.md)
- [View Model Architecture](docs/VIEW_MODEL_DESIGN.md)
- [Definition of Done](docs/DEFINITION_OF_DONE.md)
- [Project Progress Tracker](docs/PROGRESS_TRACKER.md)

---

## 🛠️ Building & Testing

### Build the entire reactor
```bash
mvn clean compile
```

### Run all unit, compiler, and integration tests
```bash
mvn clean test
```

### Run full verification
```bash
mvn clean verify
```

---

## 📄 License

GraphORM is open-source software licensed under the [Apache License, Version 2.0](LICENSE).
