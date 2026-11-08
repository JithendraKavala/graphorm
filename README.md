# GraphORM

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.2+-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![GraalVM Native](https://img.shields.io/badge/GraalVM-Native%20Image%20Ready-blueviolet.svg)](https://www.graalvm.org/)
[![GraphQL Java](https://img.shields.io/badge/GraphQL%20Java-21+-e10098.svg)](https://www.graphql-java.com//)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/badge/Build-Passing-success.svg)](https://github.com/graphorm/graphorm/actions)
[![Tests](https://img.shields.io/badge/Tests-100%25%20Passing-success.svg)](https://github.com/graphorm/graphorm/actions)

**GraphORM** is a modern, high-performance **Hybrid Schema-First ORM and Automatic GraphQL Engine** designed specifically for **Java 21+**, **Spring Boot 3+**, and **GraalVM Native Image (AOT)**.

GraphORM eliminates the traditional object-relational impedance mismatch by treating schemas as the single source of truth, compiling queries into minimal, parameterized SQL ASTs without ORM entity caching or dirty-tracking overhead.

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
- **Production-Ready Security & DoS Defense**:
  - Field-level and entity-level authorization using `@auth(roles: [...])` backed by Spring Security.
  - Backend-injected tamper-proof attributes (`@injected(from: "...")`).
  - GraphQL query depth and complexity limit instrumentations.
  - 100% SQL parameterization and portable error translation.
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
     |                          graphorm-jdbc                           |
     |     Unified Query AST, PostgreSQL Dialect, Dynamic RowMapper     |
     +---------------------------------+--------------------------------+
                                       |
                                       v
     +------------------------------------------------------------------+
     |                         graphorm-graphql                         |
     |   Generic DataFetchers, SelectionSet Pruning, DataLoader Batch   |
     +---------------------------------+--------------------------------+
                                       |
                                       v
     +------------------------------------------------------------------+
     |                   graphorm-spring-boot-starter                   |
     |     Auto-Configuration, DataSource, @Transactional, GraalVM AOT  |
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
  students Student[]

  @@map("departments")
}

model Student {
  id           Int         @id @default(autoincrement())
  name         String
  email        String      @unique
  gpa          Float?
  version      Int         @default(1) @version
  departmentId Int         @map("department_id")
  department   Department? @relation(fields: [departmentId], references: [id])

  @@map("students")
  @@index([departmentId], name: "idx_students_department")
}

view StudentDashboard {
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
```

---

## 📦 Multi-Module Organization

| Module | Description | Dependency Invariant |
|---|---|---|
| [`graphorm-core`](graphorm-core) | Metadata, AST models, query builders, portable exceptions | Zero DB/Spring dependencies |
| [`graphorm-schema`](graphorm-schema) | ANTLR4 grammar, AST visitor, validator, transformer | Core + ANTLR4 runtime only |
| [`graphorm-generator`](graphorm-generator) | JavaPoet generator for records, DTOs, and client APIs | Core + JavaPoet + JDBC |
| [`graphorm-jdbc`](graphorm-jdbc) | Dialect SQL generation, execution, row mapping, transactions, migrations | Core + `java.sql` only (No Spring) |
| [`graphorm-graphql`](graphorm-graphql) | Auto DataFetchers, DataLoaders, selection set analyzer, complexity limits | Core + JDBC + GraphQL Java |
| [`graphorm-spring-boot-starter`](graphorm-spring-boot-starter) | Spring Boot 3 auto-configuration, security authorizers, GraalVM AOT hints | Spring Boot 3 + GraphQL + JDBC |
| [`graphorm-cli`](graphorm-cli) | Picocli developer CLI (`validate`, `generate`, `init`, `introspect`, `migrate`, `fmt`, `lint`) | Core + Schema + Generator + JDBC |
| [`graphorm-maven-plugin`](graphorm-maven-plugin) | Maven build plugin for automated build-time code generation | Core + Schema + Generator + Maven Plugin API |
| [`graphorm-examples`](graphorm-examples) | End-to-end reference applications (`spring-boot-postgres`, `university-dashboard`) | Full stack demonstration (Spring Boot + PostgreSQL + React 18) |

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
