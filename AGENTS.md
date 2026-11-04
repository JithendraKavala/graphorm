# GraphORM: Global AI Engineering Rules

This file establishes the foundational, non-negotiable guidelines and engineering protocols for all AI coding agents working on the GraphORM project. Every agent must adhere strictly to these principles before and during code synthesis.

---

## The 19 Global Engineering Rules

1. **Read Relevant Skills Before Implementing**: Always load and consult the applicable skill(s) under `.agents/skills/` before designing or writing code for any module or feature. For view models, always load `graphorm-view-models`.
2. **Architecture as Source of Truth**: Treat the system architecture and architectural decisions documented in `docs/ARCHITECTURE.md` and `docs/ARCHITECTURAL_DECISIONS.md` as the definitive source of truth.
3. **Verify Before Assuming**: Do not invent APIs, classes, or assume functionality exists without verifying existing source code and tests.
4. **No Duplicate Abstractions**: Never create duplicate models, interfaces, AST structures, or query representations. Leverage existing abstractions.
5. **No Database-Specific Code in Core**: Keep `graphorm-core` completely free of database-specific dependencies (e.g., PostgreSQL, MySQL, H2 specifics or JDBC drivers).
6. **No Spring Dependencies in Core**: `graphorm-core`, `graphorm-schema`, `graphorm-generator`, and `graphorm-jdbc` must remain independent of Spring framework dependencies. Spring integration belongs exclusively in `graphorm-spring-boot-starter`.
7. **Decouple GraphQL from JDBC Execution**: Keep GraphQL execution concerns (resolvers, DataFetchers, DataLoaders) strictly decoupled from low-level JDBC query generation and statement execution.
8. **Pragmatic Dependency Inversion**: Use interfaces and dependency inversion where they provide genuine architectural boundaries and extensibility (e.g., `SqlDialect`, `TypeConverter`, `ModelRegistry`), but avoid unnecessary indirection.
9. **Avoid Premature Optimization and Over-Engineering**: Write clean, maintainable, straightforward code first. Optimize based on profiling and selection-set efficiency rather than speculative abstractions.
10. **Immutable Metadata**: Treat all schema and model metadata objects (`ModelMetadata`, `ViewModelMetadata`, `FieldMetadata`, `RelationMetadata`, `SchemaAST`) as immutable once parsed and registered.
11. **Strict View Model Read-Only Invariant**: View models (`view`) are strictly read-only projections. Never generate or expose write mutations (`create`, `update`, `delete`) or write DTOs for view models in the Java client, GraphQL registrar, or JDBC builders.
12. **Unified Query Architecture**: Never introduce separate query execution engines for regular models and view models. Both must share the unified query AST and planner in `graphorm-jdbc` and `graphorm-graphql`.
13. **Strict Java Naming Conventions**: Follow standard Java 21 conventions: PascalCase for types, camelCase for methods and variables, UPPER_SNAKE_CASE for constants. Class and interface names must accurately reflect their single responsibility.
14. **Constructor Injection**: Prefer constructor injection with `final` fields across all components and Spring beans. Avoid field injection (`@Autowired` on private fields).
15. **Meaningful Error Handling**: Throw descriptive, strongly typed domain exceptions (extending `GraphOrmException`). Always include actionable diagnostic messages with schema line numbers or query context when applicable.
16. **Mandatory Test Coverage as Much as Possible**: Everything must be tested as thoroughly as possible. No class, parser rule, query type, builder, dialect function, or code generation template is complete without exhaustive unit tests, dialect SQL assertions, Testcontainers PostgreSQL integration tests, and GraphQL N+1 regression assertions. Bug fixes must include regression tests.
17. **No Silent Architectural Shifts**: Do not alter architectural boundaries, module structures, or foundational designs without explicitly updating documentation and getting developer confirmation.
18. **No Speculative Phase Implementation**: Strictly work within the assigned roadmap phase. Never jump ahead to implement future roadmap phases simply because they are listed in `docs/IMPLEMENTATION_ROADMAP.md`.
19. **Documentation Synchronization & Status Clarity**: Keep project documentation and skills synchronized with code changes. Clearly distinguish between *Implemented*, *Partially Implemented*, *Planned*, and *Experimental* features across all code comments, PRs, and documentation.

---

## Architectural Boundaries Summary

```text
graphorm/
├── graphorm-core/               # Domain metadata, view models, AST interfaces, execution abstractions (Zero DB/Spring deps)
├── graphorm-schema/             # Lexer, Parser, AST, Schema validation (models, views, composite types)
├── graphorm-generator/          # Code generation engine (Java records, view records, clients, inputs)
├── graphorm-jdbc/               # Dialects (Postgres), Query builders, joins/aggregations, Connection/Tx wrappers, RowMappers
├── graphorm-graphql/            # GraphQL Java engine, DataFetcher generation, Selection-set analysis, DataLoader
├── graphorm-spring-boot-starter/# Spring Boot 3+ AutoConfiguration, DataSource binding, Spring for GraphQL integration
├── graphorm-cli/                # Command-line interface for codegen, schema validation, linting
└── graphorm-examples/           # End-to-end sample applications and integration tests
```
