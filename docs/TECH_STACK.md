# GraphORM: Technology Stack & Technical Baselines

## 1. Core Technical Baselines

| Component | Choice | Justification |
|---|---|---|
| **Language Runtime** | Java 21 (LTS) | Modern language features: Records, Pattern Matching, Sealed Interfaces, Virtual Threads compatibility. |
| **Build System** | Apache Maven 3.9+ | Standard multi-module architecture, robust dependency management, enterprise adoption. |
| **Parser Tooling** | ANTLR 4.13+ | Industry-standard parser generator for custom domain-specific schema languages. |
| **Code Generation** | JavaPoet 1.13+ | Strongly-typed Java source code generator producing clean, idiomatic Java 21 classes and records. |
| **Database Access** | Pure JDBC (`javax.sql.DataSource`) | Full control over SQL generation, zero ORM caching friction, predictable performance. |
| **Initial Database Target** | PostgreSQL 15+ | Leading open-source relational database; supports rich data types and standard SQL. |
| **Future Database Targets** | MySQL 8+, H2 | Extensible dialect architecture designed for multiple SQL engines. |
| **GraphQL Engine** | GraphQL Java 21+ | The standard, robust GraphQL execution library in the Java ecosystem. |
| **Spring Framework** | Spring Boot 3.2+ / Spring for GraphQL | Native Spring Boot auto-configuration and first-class GraphQL web transport support. |
| **Developer CLI** | Picocli 4.7+ | Lightweight, powerful framework for rich terminal commands and options. |
| **Observability** | Micrometer 1.12+ | Vendor-neutral application metrics for query execution times and DataLoader batch counts. |
| **Testing Framework** | JUnit 5 & AssertJ | Modern testing paradigms, fluent assertions, parameterization. |
| **Integration Testing** | Testcontainers (PostgreSQL 16) | Containerized real-database integration tests ensuring reliable SQL execution. |

---

## 2. Dependency Management Rules

1. **`graphorm-core`**:
   - Zero external runtime dependencies (except logging facade `org.slf4j:slf4j-api`).
   - No Spring dependencies.
   - No JDBC or driver dependencies.
2. **`graphorm-schema`**:
   - Only `org.antlr:antlr4-runtime` and `graphorm-core`.
3. **`graphorm-generator`**:
   - Only `com.squareup:javapoet` and `graphorm-core`.
4. **`graphorm-jdbc`**:
   - Standard JDBC APIs (`java.sql`), `graphorm-core`. No Spring JDBC dependencies.
5. **`graphorm-graphql`**:
   - `com.graphql-java:graphql-java`, `graphorm-core`, `graphorm-jdbc`.
6. **`graphorm-spring-boot-starter`**:
   - Spring Boot AutoConfigure, Spring for GraphQL, Spring JDBC (for transaction synchronization).
7. **`graphorm-cli`**:
   - `info.picocli:picocli`, `graphorm-schema`, `graphorm-generator`, `graphorm-jdbc`.
