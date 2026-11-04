# GraphORM: Module Structure & Boundaries

## 1. Repository Layout

```text
graphorm/
├── pom.xml                               # Root multi-module POM
├── AGENTS.md                             # Global AI engineering rules
├── docs/                                 # Architectural and design documentation
├── .agents/skills/                       # Antigravity AI engineering skills
├── graphorm-core/                        # Foundational metadata, view models, and execution models
├── graphorm-schema/                      # Schema parser, AST, view/model validation engine
├── graphorm-generator/                   # Code generation engine (Java models, views, clients)
├── graphorm-jdbc/                        # SQL generation, dialects, view query builders, and JDBC execution
├── graphorm-graphql/                     # GraphQL mapping, query planning, DataFetchers
├── graphorm-spring-boot-starter/         # Spring Boot 3+ auto-configuration
├── graphorm-cli/                         # Developer CLI for code generation and linting
└── graphorm-examples/                    # Sample applications
    └── spring-boot-postgres/             # End-to-end example with PostgreSQL
```

---

## 2. Package Organization & Module Responsibilities

### 2.1 `graphorm-core`
- **Root Package**: `com.graphorm.core`
- **Sub-packages**:
  - `com.graphorm.core.metadata`: `ModelMetadata`, `ViewModelMetadata`, `CompositeTypeMetadata`, `FieldMetadata`, `RelationMetadata`, `EnumMetadata`, `ModelRegistry`.
  - `com.graphorm.core.query`: `SqlQuery`, `SelectQuery`, `SourceTable`, `JoinClause`, `ProjectedColumn`, `WherePredicate`, `GroupByClause`, `OrderByClause`, `InsertQuery`, `UpdateQuery`, `DeleteQuery`.
  - `com.graphorm.core.exception`: `GraphOrmException`, `ConfigurationException`, `ReadOnlyViewException`.
- **Dependency Invariant**: Zero external dependencies (only `slf4j-api`). No Spring, no JDBC drivers.

### 2.2 `graphorm-schema`
- **Root Package**: `com.graphorm.schema`
- **Sub-packages**:
  - `com.graphorm.schema.parser`: `GraphOrmParserFacade`, `GraphOrmAstVisitor`, `DiagnosticErrorListener`.
  - `com.graphorm.schema.ast`: `SchemaDocument`, `ModelNode`, `ViewModelNode`, `CompositeTypeNode`, `FieldNode`, `AttributeNode`.
  - `com.graphorm.schema.validation`: `SchemaValidator`, `CycleDetector`, `Diagnostic`.
  - `com.graphorm.schema.transformer`: `SchemaToMetadataTransformer`.
  - `com.graphorm.schema.exception`: `SchemaValidationException`.
- **Grammar Location**: `src/main/antlr4/com/graphorm/schema/GraphOrm.g4`.
- **Dependency Invariant**: Depends only on `graphorm-core` and `antlr4-runtime`.

### 2.3 `graphorm-generator`
- **Root Package**: `com.graphorm.generator`
- **Sub-packages**:
  - `com.graphorm.generator.engine`: `CodeGeneratorEngine`.
  - `com.graphorm.generator.model`: `ModelRecordGenerator`, `ViewModelRecordGenerator`, `CompositeTypeGenerator`.
  - `com.graphorm.generator.input`: `InputDtoGenerator` (strictly for regular models).
  - `com.graphorm.generator.filter`: `FilterCriteriaGenerator`.
  - `com.graphorm.generator.client`: `ClientApiGenerator` (`GraphOrmClient`).
- **Dependency Invariant**: Depends on `graphorm-core`, `javapoet`.

### 2.4 `graphorm-jdbc`
- **Root Package**: `com.graphorm.jdbc`
- **Sub-packages**:
  - `com.graphorm.jdbc.dialect`: `SqlDialect`, `PostgreSqlDialect`.
  - `com.graphorm.jdbc.builder`: `SelectBuilder`, `InsertBuilder`, `UpdateBuilder`, `DeleteBuilder`, `WhereClauseBuilder`, `OrderByClauseBuilder`.
  - `com.graphorm.jdbc.executor`: `JdbcExecutor`, `BoundSql`.
  - `com.graphorm.jdbc.mapper`: `DynamicRowMapper`.
  - `com.graphorm.jdbc.relation`: `BatchRelationLoader`.
  - `com.graphorm.jdbc.transaction`: `JdbcTransactionManager`.
- **Dependency Invariant**: Depends on `graphorm-core`, standard JDBC (`java.sql`). No Spring Framework dependencies.

### 2.5 `graphorm-graphql`
- **Root Package**: `com.graphorm.graphql`
- **Sub-packages**:
  - `com.graphorm.graphql.registry`: `GraphOrmGraphQlRegistrar`.
  - `com.graphorm.graphql.fetcher`: `GenericListFetcher`, `GenericSingleFetcher`, `GenericCreateFetcher`, `GenericUpdateFetcher`, `GenericDeleteFetcher`, `GenericViewListFetcher`, `GenericViewSingleFetcher`.
  - `com.graphorm.graphql.planner`: `QueryPlanner`, `SelectionSetAnalyzer`.
  - `com.graphorm.graphql.loader`: `BatchLoaderFactory`.
  - `com.graphorm.graphql.security`: `MaxQueryDepthInstrumentation`, `MaxQueryComplexityInstrumentation`.
- **Dependency Invariant**: Depends on `graphorm-core`, `graphorm-jdbc`, `graphql-java`.

### 2.6 `graphorm-spring-boot-starter`
- **Root Package**: `com.graphorm.spring.boot`
- **Sub-packages**:
  - `com.graphorm.spring.boot.autoconfigure`: `GraphOrmAutoConfiguration`, `GraphOrmProperties`.
  - `com.graphorm.spring.boot.graphql`: `GraphQlAutoConfigurationCustomizer`.
  - `com.graphorm.spring.boot.transaction`: `SpringTransactionManagerAdapter`.
- **AutoConfiguration Registration**: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### 2.7 `graphorm-cli`
- **Root Package**: `com.graphorm.cli`
- **Sub-packages**:
  - `com.graphorm.cli.command`: `ValidateCommand`, `GenerateCommand`, `InitCommand`, `IntrospectCommand`.
  - `com.graphorm.cli.runner`: `CliRunner`.

### 2.8 `graphorm-examples`
- Sample application: `graphorm-examples/spring-boot-postgres`
- Demonstrates complete university domain with Spring Boot 3+, PostgreSQL, Testcontainers, and GraphiQL.
