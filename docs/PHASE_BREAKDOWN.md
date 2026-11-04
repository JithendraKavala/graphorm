# GraphORM: Detailed Phase Breakdown & Task Specifications

This document defines the exhaustive, task-by-task engineering breakdown for all 16 phases of GraphORM. Every task includes technical implementation details, exact classes, dependencies, testing requirements, and completion criteria.

---

## Phase 1: Project Foundation & Multi-Module Build System

### 1.1 Phase Overview
- **Phase Number**: Phase 1
- **Objective**: Establish the Maven multi-module structure, Java 21 LTS toolchain, centralized dependency management, CI pipelines, and base exception hierarchy.
- **Why Necessary**: Provides the foundational compile, test, and release harness. Guarantees modular dependency boundaries (Rule 5 & Rule 6 in `AGENTS.md`) so that `graphorm-core` never leaks database or Spring dependencies.
- **Expected Outcome**: A compiling multi-module Maven project where all child modules inherit standardized compiler, linting, and testing configurations.

### 1.2 Technical Breakdown
- **Modules Involved**: Root `pom.xml`, `graphorm-core`, `graphorm-schema`, `graphorm-generator`, `graphorm-jdbc`, `graphorm-graphql`, `graphorm-spring-boot-starter`, `graphorm-cli`, `graphorm-examples`.
- **Functionality**:
  - Centralized `<dependencyManagement>` in root POM.
  - Java 21 compilation settings with `-parameters` and UTF-8 encoding.
  - Maven Surefire and Failsafe plugins for JUnit 5 and Testcontainers.
  - JaCoCo code coverage plugin configured.
  - Core domain exception hierarchy.
- **Important Classes & Interfaces**:
  - `com.graphorm.core.exception.GraphOrmException` (Base unchecked domain exception)
  - `com.graphorm.core.exception.ConfigurationException`
- **Architectural Decisions**: ADR-004 (Multi-Module Structure).
- **Dependencies**: None.

### 1.3 Implementation Tasks

#### Task 1.1: Root Multi-Module POM Configuration
- **Task ID**: `TASK-1.1`
- **Description**: Create the root `pom.xml` with parent POM setup, dependency management, and build plugins.
- **Technical Details**: Configure Java 21 source/target, enforce `<dependencyManagement>` for JUnit 5.10+, AssertJ 3.25+, Testcontainers 1.19+, SLF4J 2.0+, Jackson 2.16+, and declare all 8 child modules.
- **Expected Files**: `pom.xml`
- **Dependencies**: None.
- **Testing Requirements**: Run `mvn validate` and `mvn help:effective-pom`.
- **Completion Criteria**: Root POM validates cleanly with zero errors.

#### Task 1.2: Child Module Skeletons & Dependencies
- **Task ID**: `TASK-1.2`
- **Description**: Create minimal `pom.xml` descriptors for all 8 child modules adhering to boundary rules.
- **Technical Details**:
  - `graphorm-core`: Only SLF4J API.
  - `graphorm-schema`: Depends on `graphorm-core`, ANTLR4 runtime.
  - `graphorm-generator`: Depends on `graphorm-core`, JavaPoet.
  - `graphorm-jdbc`: Depends on `graphorm-core`, standard JDBC (`java.sql`).
  - `graphorm-graphql`: Depends on `graphorm-core`, GraphQL Java 21+.
  - `graphorm-spring-boot-starter`: Depends on `graphorm-core`, `graphorm-jdbc`, `graphorm-graphql`, Spring Boot Autoconfigure.
  - `graphorm-cli`: Depends on `graphorm-schema`, `graphorm-generator`, Picocli.
  - `graphorm-examples`: Sample application container module.
- **Expected Files**: `graphorm-*/pom.xml`
- **Dependencies**: `TASK-1.1`
- **Testing Requirements**: Run `mvn clean compile` across all modules.
- **Completion Criteria**: Full project compiles cleanly with zero dependencies leaking into core.

#### Task 1.3: Base Exception Hierarchy in `graphorm-core`
- **Task ID**: `TASK-1.3`
- **Description**: Implement base unchecked exceptions in `com.graphorm.core.exception`.
- **Technical Details**: Implement `GraphOrmException` with message, cause, and optional error code. Implement `ConfigurationException`.
- **Expected Files**:
  - `graphorm-core/src/main/java/com/graphorm/core/exception/GraphOrmException.java`
  - `graphorm-core/src/main/java/com/graphorm/core/exception/ConfigurationException.java`
- **Dependencies**: `TASK-1.2`
- **Testing Requirements**: Unit test asserting exception inheritance, message propagation, and stack trace preservation.
- **Completion Criteria**: 100% test pass rate in `graphorm-core`.

### 1.4 Milestone Deliverable
- Clean Maven multi-module build executing `mvn clean test` across all modules with zero warnings.

---

## Phase 2: Core Metadata & Unified Query AST (`graphorm-core`)

### 2.1 Phase Overview
- **Phase Number**: Phase 2
- **Objective**: Design and build the runtime metadata model (`ModelMetadata`, `ViewModelMetadata`, `ModelRegistry`) and the strongly typed unified SQL Query AST.
- **Why Necessary**: Establishes the immutable contract shared by the parser, generator, query planner, and execution engine. Ensures regular models and view models share the same query representation (ADR-003, ADR-004, ADR-005).
- **Expected Outcome**: Completely immutable metadata records and a composable SQL Query AST that represents tables, views, joins, projections, and predicates without database-specific syntax.

### 2.2 Technical Breakdown
- **Modules Involved**: `graphorm-core`
- **Functionality**:
  - In-memory immutable metadata structures: `ModelMetadata`, `ViewModelMetadata`, `FieldMetadata`, `RelationMetadata`, `CompositeTypeMetadata`, `EnumMetadata`.
  - Immutable thread-safe `ModelRegistry`.
  - Query AST: `SelectQuery`, `SourceTable`, `ProjectedColumn`, `JoinClause`, `WherePredicate`, `GroupByClause`, `OrderByClause`.
  - Write query representations: `InsertQuery`, `UpdateQuery`, `DeleteQuery`.
  - Read-only guard: marker/contract guaranteeing view models cannot be targeted by write queries.
- **Important Classes & Interfaces**:
  - `com.graphorm.core.metadata.ModelMetadata`
  - `com.graphorm.core.metadata.ViewModelMetadata`
  - `com.graphorm.core.metadata.FieldMetadata`
  - `com.graphorm.core.metadata.RelationMetadata`
  - `com.graphorm.core.metadata.ModelRegistry`
  - `com.graphorm.core.query.SelectQuery`
  - `com.graphorm.core.query.SourceTable`
  - `com.graphorm.core.query.JoinClause`
  - `com.graphorm.core.query.ProjectedColumn`
  - `com.graphorm.core.query.WherePredicate`
  - `com.graphorm.core.exception.ReadOnlyViewException`
- **Architectural Decisions**: ADR-003, ADR-004, ADR-005, ADR-008.
- **Dependencies**: Phase 1.

### 2.3 Implementation Tasks

#### Task 2.1: Field, Relation & Enum Metadata Records
- **Task ID**: `TASK-2.1`
- **Description**: Implement immutable records for fields, relationships, and enums.
- **Technical Details**:
  - `FieldMetadata`: name, javaType, dbColumnName, isPrimaryKey, isNullable, isUnique, isGenerated, defaultValue.
  - `RelationMetadata`: relationName, relationType (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_ONE, MANY_TO_MANY), sourceModel, targetModel, foreignKeyFields, referenceFields.
  - `EnumMetadata`: enumName, values.
- **Expected Files**: `graphorm-core/src/main/java/com/graphorm/core/metadata/*`
- **Dependencies**: `TASK-1.3`
- **Testing Requirements**: Immutability tests (assert collections are unmodifiable, defensive copies).
- **Completion Criteria**: All metadata records unit tested with 100% mutation defense.

#### Task 2.2: Model, View Model & Composite Type Metadata
- **Task ID**: `TASK-2.2`
- **Description**: Implement `ModelMetadata`, `ViewModelMetadata`, and `CompositeTypeMetadata`.
- **Technical Details**:
  - Common interface `EntityMetadata`.
  - `ModelMetadata`: name, tableName, fields, primaryKeyFields, uniqueConstraints, relations.
  - `ViewModelMetadata`: name, viewName, fields, sourceModelName, joinDefinitions, groupByColumns, isDatabaseBacked, isMaterialized.
  - `CompositeTypeMetadata`: name, fields.
- **Expected Files**: `graphorm-core/src/main/java/com/graphorm/core/metadata/*`
- **Dependencies**: `TASK-2.1`
- **Testing Requirements**: Verification of model vs view metadata separation and property lookups.
- **Completion Criteria**: Complete metadata representations created and verified.

#### Task 2.3: In-Memory `ModelRegistry`
- **Task ID**: `TASK-2.3`
- **Description**: Build thread-safe `ModelRegistry` holding models, views, and composite types.
- **Technical Details**: Provide lookup methods: `getModel(String)`, `getViewModel(String)`, `getEntity(String)`, `getAllModels()`, `getAllViews()`. Support builder pattern for immutable construction.
- **Expected Files**: `graphorm-core/src/main/java/com/graphorm/core/metadata/ModelRegistry.java`
- **Dependencies**: `TASK-2.2`
- **Testing Requirements**: Concurrent access tests, unknown entity lookups throwing descriptive exceptions.
- **Completion Criteria**: Thread-safe registry passing all concurrent read tests.

#### Task 2.4: Unified Query AST Definition
- **Task ID**: `TASK-2.4`
- **Description**: Implement the composable `SelectQuery` AST supporting single tables and multi-table views.
- **Technical Details**:
  - `SelectQuery`: source (`SourceTable`), alias, columns (`List<ProjectedColumn>`), joins (`List<JoinClause>`), where (`WherePredicate`), groupBy, having, orderBy, limit, offset.
  - `SourceTable`: sealed interface permitting `PhysicalTable`, `DatabaseView`, and `SubquerySource`.
  - `ProjectedColumn`: expression, alias, isHiddenDependency.
  - `JoinClause`: joinType (`INNER`, `LEFT`, `RIGHT`), table, alias, condition.
  - Write queries: `InsertQuery`, `UpdateQuery`, `DeleteQuery`.
  - Read-only safety: helper verifying whether a target entity is read-only.
- **Expected Files**: `graphorm-core/src/main/java/com/graphorm/core/query/*`
- **Dependencies**: `TASK-2.3`
- **Testing Requirements**: AST construction tests, equality and hashcode tests, serialization checks.
- **Completion Criteria**: Full Query AST implemented with complete test coverage.

### 2.4 Milestone Deliverable
- Programmatically construct `ModelRegistry` containing a `Student` model and `StudentDashboard` view, construct a `SelectQuery` AST with joins and projected columns, and assert its immutability.

---

## Phase 3: Schema Language Lexer, Parser & Semantic Validator (`graphorm-schema`)

### 3.1 Phase Overview
- **Phase Number**: Phase 3
- **Objective**: Implement the ANTLR4 grammar, tokenization, AST visitor, semantic validation, and metadata transformation for `schema.graphorm`.
- **Why Necessary**: Enables developers to declare database models, views, composite types, and relations in a clean Prisma-like syntax instead of writing Java boilerplate.
- **Expected Outcome**: A robust parser that converts `schema.graphorm` files into an AST, validates all semantic rules, produces rich line/column error diagnostics, and generates a valid `ModelRegistry`.

### 3.2 Technical Breakdown
- **Modules Involved**: `graphorm-schema`, `graphorm-core`
- **Functionality**:
  - ANTLR4 grammar (`GraphOrm.g4`) covering `datasource`, `generator`, `model`, `view`, `type`, `enum`, field types, attributes (`@id`, `@map`, `@relation`, `@aggregate`, `@expression`), and block attributes (`@@map`, `@@view`, `@@from`, `@@leftJoin`, `@@groupBy`).
  - AST node hierarchy: `SchemaDocument`, `ModelNode`, `ViewModelNode`, `CompositeTypeNode`, `FieldNode`, `AttributeNode`.
  - Semantic Validator:
    - Primary key checks on models.
    - Foreign key and relation symmetry checks.
    - View source model and join predicate validation.
    - Read-only invariant check: reject `@default(autoincrement())` on views.
    - Cyclic view dependency detection using Tarjan's or Kahn's algorithm.
  - `SchemaToMetadataTransformer`: converts validated AST into `ModelRegistry`.
- **Important Classes & Interfaces**:
  - `com.graphorm.schema.parser.GraphOrmParserFacade`
  - `com.graphorm.schema.ast.SchemaDocument`
  - `com.graphorm.schema.validation.SchemaValidator`
  - `com.graphorm.schema.validation.Diagnostic`
  - `com.graphorm.schema.transformer.SchemaToMetadataTransformer`
  - `com.graphorm.schema.exception.SchemaValidationException`
- **Architectural Decisions**: ADR-001, ADR-002, ADR-010.
- **Dependencies**: Phase 2.

### 3.3 Implementation Tasks

#### Task 3.1: ANTLR4 Grammar (`GraphOrm.g4`)
- **Task ID**: `TASK-3.1`
- **Description**: Define the formal ANTLR4 grammar for lexical analysis and syntactic parsing.
- **Technical Details**: Define tokens for keywords (`model`, `view`, `type`, `enum`, `datasource`, `generator`), identifiers, string literals, integer literals, float literals, operators, and comments (`//`). Define parser rules for model blocks, view blocks, attributes with named and positional arguments.
- **Expected Files**: `graphorm-schema/src/main/antlr4/com/graphorm/schema/GraphOrm.g4`
- **Dependencies**: `TASK-2.4`
- **Testing Requirements**: Parse sample schemas containing models, views, and composite types.
- **Completion Criteria**: ANTLR4 Maven plugin generates Java lexer and parser classes without grammar ambiguity warnings.

#### Task 3.2: AST Node Hierarchy & Visitor
- **Task ID**: `TASK-3.2`
- **Description**: Implement strongly-typed AST nodes and an ANTLR visitor to populate the AST from parse trees.
- **Technical Details**: Implement `SchemaDocument`, `ModelNode`, `ViewModelNode`, `CompositeTypeNode`, `FieldNode`, `AttributeNode` with source coordinate tracking (file, startLine, startCol, endLine, endCol).
- **Expected Files**:
  - `graphorm-schema/src/main/java/com/graphorm/schema/ast/*`
  - `graphorm-schema/src/main/java/com/graphorm/schema/parser/GraphOrmAstVisitor.java`
- **Dependencies**: `TASK-3.1`
- **Testing Requirements**: Unit test visiting complete schema files and asserting exact AST structure.
- **Completion Criteria**: 100% of grammar features correctly converted to AST nodes.

#### Task 3.3: Diagnostic Error Reporting & Syntax Listener
- **Task ID**: `TASK-3.3`
- **Description**: Implement custom ANTLR `BaseErrorListener` to collect syntax errors with line/column coordinates.
- **Technical Details**: Capture syntax errors, format friendly error messages pointing to source line excerpts with caret indicators (`^`).
- **Expected Files**:
  - `graphorm-schema/src/main/java/com/graphorm/schema/parser/DiagnosticErrorListener.java`
  - `graphorm-schema/src/main/java/com/graphorm/schema/validation/Diagnostic.java`
- **Dependencies**: `TASK-3.2`
- **Testing Requirements**: Parse malformed schemas (missing braces, unclosed quotes, illegal keywords) and assert formatted diagnostic messages.
- **Completion Criteria**: Accurate line/column error reporting verified for syntax errors.

#### Task 3.4: Semantic Validation Engine
- **Task ID**: `TASK-3.4`
- **Description**: Implement `SchemaValidator` to enforce business, relational, and view model invariants.
- **Technical Details**:
  - Validate model primary keys (exactly one `@id` or `@@id`).
  - Validate relationship symmetry (bidirectional relations match fields/references).
  - Enforce read-only view invariants (no write defaults, no autoincrement).
  - Validate view sources: all models in `@@from`, `@@leftJoin` exist in schema.
  - Cycle detection: detect circular view references (`ViewA -> ViewB -> ViewA`).
- **Expected Files**:
  - `graphorm-schema/src/main/java/com/graphorm/schema/validation/SchemaValidator.java`
  - `graphorm-schema/src/main/java/com/graphorm/schema/validation/CycleDetector.java`
- **Dependencies**: `TASK-3.3`
- **Testing Requirements**: Comprehensive test cases for valid schemas and each validation failure condition.
- **Completion Criteria**: All semantic validation rules covered by unit tests.

#### Task 3.5: Schema-to-Metadata Transformer
- **Task ID**: `TASK-3.5`
- **Description**: Convert validated `SchemaDocument` AST into an immutable `ModelRegistry`.
- **Technical Details**: Map `ModelNode` to `ModelMetadata`, `ViewModelNode` to `ViewModelMetadata`, resolve foreign key field types, parse aggregate directives into structured field metadata.
- **Expected Files**: `graphorm-schema/src/main/java/com/graphorm/schema/transformer/SchemaToMetadataTransformer.java`
- **Dependencies**: `TASK-3.4`
- **Testing Requirements**: Assert that parsed schema outputs an exact, queryable `ModelRegistry`.
- **Completion Criteria**: End-to-end transformation tested from schema string to `ModelRegistry`.

### 3.4 Milestone Deliverable
- Parse a complete `schema.graphorm` file containing `Student`, `Department`, `StudentDashboard` (view), and `Address` (type), validate it, and produce an immutable `ModelRegistry`.

---

## Phase 4: JDBC Database Engine, SQL Dialects & Basic CRUD (`graphorm-jdbc`)

### 4.1 Phase Overview
- **Phase Number**: Phase 4
- **Objective**: Implement JDBC execution, SQL dialect generation for PostgreSQL, query builders, dynamic row mapping, and transaction wrappers.
- **Why Necessary**: Connects the framework to real relational databases. Provides the core persistence pipeline without ORM overhead or Hibernate magic (ADR-002, ADR-005, ADR-006).
- **Expected Outcome**: A functional JDBC engine capable of executing CRUD operations on models against a real PostgreSQL database with 100% parameterized SQL.

### 4.2 Technical Breakdown
- **Modules Involved**: `graphorm-jdbc`, `graphorm-core`
- **Functionality**:
  - `SqlDialect` interface and `PostgreSqlDialect` implementation (identifier double-quoting, parameter placeholders `?`, `LIMIT ? OFFSET ?`, `RETURNING *`).
  - Query builders: `SelectBuilder`, `InsertBuilder`, `UpdateBuilder`, `DeleteBuilder`.
  - Parameterized `BoundSql` generation.
  - `DynamicRowMapper`: mapping `ResultSet` columns to Maps and records.
  - `JdbcExecutor`: connection management, prepared statements, resource closing.
  - Transaction boundary abstraction: `JdbcTransactionManager`.
  - Read-only protection: `InsertBuilder`, `UpdateBuilder`, `DeleteBuilder` fail if applied to `ViewModelMetadata`.
- **Important Classes & Interfaces**:
  - `com.graphorm.jdbc.dialect.SqlDialect`
  - `com.graphorm.jdbc.dialect.PostgreSqlDialect`
  - `com.graphorm.jdbc.builder.SelectBuilder`
  - `com.graphorm.jdbc.builder.InsertBuilder`
  - `com.graphorm.jdbc.builder.UpdateBuilder`
  - `com.graphorm.jdbc.builder.DeleteBuilder`
  - `com.graphorm.jdbc.executor.BoundSql`
  - `com.graphorm.jdbc.executor.JdbcExecutor`
  - `com.graphorm.jdbc.mapper.DynamicRowMapper`
  - `com.graphorm.jdbc.transaction.JdbcTransactionManager`
- **Architectural Decisions**: ADR-002, ADR-005, ADR-006, ADR-007, ADR-011.
- **Dependencies**: Phase 2, Phase 3.

### 4.3 Implementation Tasks

#### Task 4.1: `SqlDialect` & `PostgreSqlDialect`
- **Task ID**: `TASK-4.1`
- **Description**: Implement `SqlDialect` abstraction and `PostgreSqlDialect`.
- **Technical Details**: Double quote identifiers (`"students"`), format limit/offset (`LIMIT ? OFFSET ?`), render positional `?` placeholders, render RETURNING clauses for inserts.
- **Expected Files**:
  - `graphorm-jdbc/src/main/java/com/graphorm/jdbc/dialect/SqlDialect.java`
  - `graphorm-jdbc/src/main/java/com/graphorm/jdbc/dialect/PostgreSqlDialect.java`
- **Dependencies**: `TASK-2.4`
- **Testing Requirements**: Exact SQL string assertions for quoting, limit/offset, and keywords.
- **Completion Criteria**: 100% unit test coverage for PostgreSQL dialect formatting.

#### Task 4.2: CRUD Query Builders & Parameter Extraction
- **Task ID**: `TASK-4.2`
- **Description**: Build `SelectBuilder`, `InsertBuilder`, `UpdateBuilder`, and `DeleteBuilder` producing parameterized `BoundSql`.
- **Technical Details**: Convert query ASTs into `BoundSql(sql, parameters)`. Throw `ReadOnlyViewException` if write builders target a view model.
- **Expected Files**: `graphorm-jdbc/src/main/java/com/graphorm/jdbc/builder/*`
- **Dependencies**: `TASK-4.1`
- **Testing Requirements**: Test SQL output and parameter ordering for SELECT, INSERT, UPDATE, DELETE. Test that views throw `ReadOnlyViewException` on write builders.
- **Completion Criteria**: All CRUD builders verified with dialect unit tests.

#### Task 4.3: `DynamicRowMapper` & Type Conversion
- **Task ID**: `TASK-4.3`
- **Description**: Implement `DynamicRowMapper` converting JDBC `ResultSet` rows into Maps and Java objects.
- **Technical Details**: Map column types (Postgres integers, varchars, timestamps, booleans) to corresponding Java types based on `FieldMetadata`. Handle nulls correctly.
- **Expected Files**: `graphorm-jdbc/src/main/java/com/graphorm/jdbc/mapper/DynamicRowMapper.java`
- **Dependencies**: `TASK-4.2`
- **Testing Requirements**: Mock `ResultSet` tests covering all supported scalar types and null handling.
- **Completion Criteria**: Robust row mapper tested across all scalar types.

#### Task 4.4: `JdbcExecutor` & Resource Management
- **Task ID**: `TASK-4.4`
- **Description**: Implement `JdbcExecutor` executing `BoundSql` against `javax.sql.DataSource`.
- **Technical Details**: Use try-with-resources for `Connection`, `PreparedStatement`, and `ResultSet`. Bind positional parameters, execute queries, return mapped lists/objects, and translate `SQLException` to `QueryExecutionException`.
- **Expected Files**:
  - `graphorm-jdbc/src/main/java/com/graphorm/jdbc/executor/JdbcExecutor.java`
  - `graphorm-jdbc/src/main/java/com/graphorm/jdbc/exception/QueryExecutionException.java`
- **Dependencies**: `TASK-4.3`
- **Testing Requirements**: Unit tests with mock DataSource, parameter binding verification.
- **Completion Criteria**: Resource leak-free JDBC executor implemented.

#### Task 4.5: Testcontainers PostgreSQL CRUD Integration Tests
- **Task ID**: `TASK-4.5`
- **Description**: Build real PostgreSQL integration test suite using Testcontainers.
- **Technical Details**: Spin up `postgres:16-alpine`, execute DDL (`CREATE TABLE students ...`), perform INSERT, SELECT by ID, SELECT all, UPDATE, and DELETE via `JdbcExecutor`.
- **Expected Files**: `graphorm-jdbc/src/test/java/com/graphorm/jdbc/PostgresCrudIntegrationTest.java`
- **Dependencies**: `TASK-4.4`
- **Testing Requirements**: End-to-end CRUD test suite running against PostgreSQL in Docker.
- **Completion Criteria**: All CRUD operations pass against real PostgreSQL.

### 4.4 Milestone Deliverable: **Milestone 1**
- Define `Student` model in `schema.graphorm`, parse it into `ModelRegistry`, connect to PostgreSQL via Testcontainers, insert a student record, and query it back without writing a DAO, RowMapper, or entity class.

---

## Phase 5: Filtering, Sorting & Pagination Pushdown (`graphorm-jdbc`, `graphorm-core`)

### 5.1 Phase Overview
- **Phase Number**: Phase 5
- **Objective**: Implement composable filter predicate trees (`WherePredicate`), ordering criteria (`OrderByClause`), and database pushdown pagination.
- **Why Necessary**: Prevents in-memory filtering and full-table scans. Ensures that complex filters, sorting, and pagination are executed entirely inside PostgreSQL.
- **Expected Outcome**: Rich criteria engine supporting relational and logical operators pushed down directly to SQL `WHERE`, `ORDER BY`, and `LIMIT/OFFSET`.

### 5.2 Technical Breakdown
- **Modules Involved**: `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - Relational operators: `eq`, `neq`, `gt`, `gte`, `lt`, `lte`, `in`, `notIn`, `like`, `contains`, `startsWith`, `endsWith`, `isNull`, `isNotNull`.
  - Logical operators: `and`, `or`, `not` (recursive tree evaluation).
  - `OrderByClause`: column, direction (`ASC`, `DESC`), nulls ordering (`NULLS FIRST`, `NULLS LAST`).
  - Parameter binding in predicate trees.
  - Pushdown pagination (`LIMIT ? OFFSET ?`).
- **Important Classes & Interfaces**:
  - `com.graphorm.core.query.WherePredicate`
  - `com.graphorm.core.query.ComparisonOperator`
  - `com.graphorm.core.query.OrderByClause`
  - `com.graphorm.jdbc.builder.WhereClauseBuilder`
- **Dependencies**: Phase 4.

### 5.3 Implementation Tasks
- **TASK-5.1**: Implement `WherePredicate` composable node tree in `graphorm-core` (supporting `AND`, `OR`, `NOT`, and all comparison operators).
- **TASK-5.2**: Implement `WhereClauseBuilder` in `graphorm-jdbc` to render parameterized SQL `WHERE` clauses with parameter binding.
- **TASK-5.3**: Implement `OrderByClauseBuilder` supporting multi-column sorting and nulls positioning.
- **TASK-5.4**: Testcontainers integration test asserting multi-condition queries, sorting, and pagination against PostgreSQL.
- **Completion Criteria**: Multi-condition filters (`age >= 18 AND (name LIKE 'A%' OR status = 'ACTIVE')`) execute with correct parameter bindings and pagination on PostgreSQL.

---

## Phase 6: Relationship Metadata & Batched Relationship Loading (`graphorm-core`, `graphorm-jdbc`)

### 6.1 Phase Overview
- **Phase Number**: Phase 6
- **Objective**: Implement relationship definitions (1:1, 1:N, N:1, N:M), relationship traversal planning, and batched loading (`WHERE foreign_key IN (?, ...)`).
- **Why Necessary**: Eliminates N+1 query bottlenecks and prevents cartesian explosion from multi-table joins.
- **Expected Outcome**: A batch relation loading engine capable of resolving nested relationships in exactly 2 SQL queries (1 primary + 1 batch).

### 6.2 Technical Breakdown
- **Modules Involved**: `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - `RelationPlanner`: generates batch query for related entity keys.
  - Multi-key batching (`WHERE foreign_key IN (?, ?, ...)`).
  - Result grouping and parent association in memory.
  - Circular relationship defense (preventing infinite loops).
  - SQL query counter to verify statement counts.
- **Dependencies**: Phase 5.

### 6.3 Implementation Tasks
- **TASK-6.1**: Implement `RelationPlanner` in `graphorm-core` for 1:1, 1:N, and N:1 relations.
- **TASK-6.2**: Implement `BatchRelationLoader` in `graphorm-jdbc` executing batched IN queries.
- **TASK-6.3**: Implement SQL statement counter test utility to track query executions.
- **TASK-6.4**: Testcontainers integration tests verifying that loading 100 students with departments executes exactly 2 SQL statements.
- **Completion Criteria**: Zero N+1 query regression proven by statement assertion tests.

---

## Phase 7: Database-Backed View Models (Stage 1 Views) (`graphorm-jdbc`, `graphorm-schema`)

### 7.1 Phase Overview
- **Phase Number**: Phase 7
- **Objective**: Implement Stage 1 of View Models: database-backed views (`@@view`, `@@map("table_or_view")`).
- **Why Necessary**: Enables querying existing PostgreSQL views or materialized views without handwritten DTOs, while enforcing strict read-only guarantees (ADR-008, ADR-010).
- **Expected Outcome**: Developers can declare `view` mapped to existing PostgreSQL views, execute filtered and paginated queries, with mutations rejected.

### 7.2 Technical Breakdown
- **Modules Involved**: `graphorm-schema`, `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - Parser support for `view <Name> { ... @@view @@map("...") }`.
  - Treat database-backed views as `SourceTable.DatabaseView` in `SelectQuery`.
  - Pushdown filtering, sorting, and pagination for database views.
  - Strict read-only invariant enforcement (throw `ReadOnlyViewException` on insert/update/delete).
- **Dependencies**: Phase 4, Phase 5.

### 7.3 Implementation Tasks
- **TASK-7.1**: Schema parser & AST support for `view` blocks with `@@view` and `@@map`.
- **TASK-7.2**: `SelectBuilder` support for querying `SourceTable.DatabaseView`.
- **TASK-7.3**: Enforce read-only constraint: reject write query builders targeting view metadata.
- **TASK-7.4**: Testcontainers integration tests querying a PostgreSQL view with filters and pagination.

### 7.4 Milestone Deliverable: **Milestone 2**
- Create a PostgreSQL view `vw_student_summary`, declare it in `schema.graphorm` with `@@view`, execute queries with filtering and pagination, and verify that write operations throw `ReadOnlyViewException`.

---

## Phase 8: Virtual GraphORM-Defined Views & Dynamic Joins (Stage 2 & 3 Views) (`graphorm-jdbc`)

### 8.1 Phase Overview
- **Phase Number**: Phase 8
- **Objective**: Implement virtual views defined purely in `schema.graphorm` using `@@from`, `@@leftJoin`, and `@@innerJoin`.
- **Why Necessary**: Allows combining multiple models into read-only projections without requiring DBA privileges or physical database view creations.
- **Expected Outcome**: Dynamic construction of multi-table join trees translated to parameterized SQL with filter and pagination pushdown.

### 8.2 Technical Breakdown
- **Modules Involved**: `graphorm-schema`, `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - Schema parser support for `@@from(Model, as: "alias")`, `@@leftJoin(Model, as: "alias", on: "condition")`, `@@innerJoin`.
  - Dynamic join tree construction in `SelectQuery`.
  - Column alias resolution: mapping view field names to qualified table columns (`s.name AS studentName`).
  - Pushdown filtering and sorting mapped to source model columns.
- **Dependencies**: Phase 7.

### 8.3 Implementation Tasks
- **TASK-8.1**: Parser rules and AST nodes for `@@from`, `@@leftJoin`, `@@innerJoin`.
- **TASK-8.2**: Join condition parser and AST representation in `JoinClause`.
- **TASK-8.3**: `SelectBuilder` generation of multi-table `LEFT JOIN` and `INNER JOIN` SQL.
- **TASK-8.4**: Pushdown filter and order-by mapping from view field aliases to source table columns.
- **TASK-8.5**: Testcontainers PostgreSQL integration tests verifying multi-table joined view queries.
- **Completion Criteria**: GraphORM-defined view with 3 joined tables queries correctly in PostgreSQL.

---

## Phase 9: View Aggregations, Computed Fields & GROUP BY (Stage 4 Views) (`graphorm-jdbc`)

### 9.1 Phase Overview
- **Phase Number**: Phase 9
- **Objective**: Implement aggregations (`@aggregate(count | sum | avg | min | max)`), computed expressions (`@expression`), `GROUP BY`, `HAVING`, and join pruning.
- **Why Necessary**: Enables analytical and dashboard views calculated entirely inside the database with zero in-memory data processing.
- **Expected Outcome**: Single-query execution of aggregated views with automatic `GROUP BY` and unused join pruning.

### 9.2 Technical Breakdown
- **Modules Involved**: `graphorm-schema`, `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - Directives `@aggregate` and `@expression`.
  - Block attribute `@@groupBy(["s.id", "s.name", ...])`.
  - `PostgreSqlDialect` support for aggregation functions and `HAVING` predicates.
  - Join pruning: prune joins whose columns are not selected or referenced in aggregations.
- **Dependencies**: Phase 8.

### 9.3 Implementation Tasks
- **TASK-9.1**: Parse and validate `@aggregate`, `@expression`, and `@@groupBy`.
- **TASK-9.2**: SQL builder support for `GROUP BY`, `HAVING`, and aggregate functions.
- **TASK-9.3**: Implement join pruner in query planner to drop unselected joins.
- **TASK-9.4**: Testcontainers integration test for `StudentDashboard` calculating course count and average GPA.

### 9.4 Milestone Deliverable: **Milestone 3**
- Query `StudentDashboard` with aggregations (`COUNT`, `AVG`); assert that a single SQL query executes with `LEFT JOIN` and `GROUP BY`, returning exact calculated values.

---

## Phase 10: Automatic GraphQL Integration & Dynamic DataFetchers (`graphorm-graphql`)

### 10.1 Phase Overview
- **Phase Number**: Phase 10
- **Objective**: Automatically wire GraphQL queries and mutations to database execution using GraphQL Java and Spring for GraphQL.
- **Why Necessary**: Eliminates handwritten DataFetchers, DTOs, and argument parsing (ADR-003).
- **Expected Outcome**: Zero-boilerplate GraphQL API where models and views are automatically queryable upon startup.

### 10.2 Technical Breakdown
- **Modules Involved**: `graphorm-graphql`, `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - `GraphOrmGraphQlRegistrar`: inspects `schema.graphqls` and `ModelRegistry`.
  - Automatically registers `Query.<model>s`, `Query.<model>`.
  - Automatically registers `Mutation.create<Model>`, `update<Model>`, `delete<Model>` (regular models only).
  - Automatically registers `Query.<view>s`, `Query.<view>` for views (strictly NO mutations registered).
  - Argument binder: translates GraphQL `where`, `orderBy`, `limit`, `offset` into GraphORM AST.
  - Custom resolver coexistence: skips automatic registration if a custom DataFetcher is already present.
- **Dependencies**: Phase 6, Phase 9.

### 10.3 Implementation Tasks
- **TASK-10.1**: GraphQL SDL type inspector and model matching.
- **TASK-10.2**: Generic `ModelListDataFetcher` and `ModelSingleDataFetcher`.
- **TASK-10.3**: Generic `ModelCreateDataFetcher`, `ModelUpdateDataFetcher`, `ModelDeleteDataFetcher`.
- **TASK-10.4**: View fetchers (`ViewListDataFetcher`, `ViewSingleDataFetcher`) with strict mutation exclusion.
- **TASK-10.5**: GraphQL argument binder converting inputs to `WherePredicate` and `OrderByClause`.
- **TASK-10.6**: End-to-end GraphQL execution tests using GraphQL Java execution engine.

### 10.4 Milestone Deliverable: **Milestone 4**
- Execute GraphQL queries for models and views without writing any DataFetchers or resolvers.

---

## Phase 11: Selection-Set Query Planner & N+1 Prevention (`graphorm-graphql`)

### 11.1 Phase Overview
- **Phase Number**: Phase 11
- **Objective**: Inspect GraphQL selection sets to generate selective SQL projections, inject hidden dependency columns, and wire `DataLoader` batching for relationships.
- **Why Necessary**: Eliminates over-fetching (selecting only requested columns) and eliminates N+1 query bottlenecks on nested GraphQL queries (ADR-008, ADR-009).
- **Expected Outcome**: An optimal query planner that executes minimal SQL projections and batches nested relationship queries with zero N+1 queries.

### 11.2 Technical Breakdown
- **Modules Involved**: `graphorm-graphql`, `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - `SelectionSetAnalyzer`: extracts requested field tree from `DataFetchingFieldSelectionSet`.
  - Selective projection: SQL `SELECT` includes only requested fields.
  - Hidden dependency injection: injects foreign keys needed for downstream DataLoaders with `isHiddenDependency = true`.
  - Response assembler: strips hidden dependency keys before returning GraphQL response.
  - GraphQL Java `DataLoader` / `DataLoaderRegistry` batching.
- **Dependencies**: Phase 10.

### 11.3 Implementation Tasks
- **TASK-11.1**: Implement `SelectionSetAnalyzer` to traverse GraphQL selection sets.
- **TASK-11.2**: Implement selective column projection builder in `QueryPlanner`.
- **TASK-11.3**: Implement hidden foreign key dependency injection.
- **TASK-11.4**: Wire `DataLoaderRegistry` with `BatchRelationLoader`.
- **TASK-11.5**: Integration tests asserting exact SQL projection and zero N+1 queries on nested queries.

### 11.4 Milestone Deliverable: **Milestone 5**
- Execute `{ students { name department { name } } }`. Verify via SQL assertion that exactly 2 SQL statements execute, projecting only `name, department_id` for students and `id, name` for departments.

---

## Phase 12: Java Client Code Generator (`graphorm-generator`)

### 12.1 Phase Overview
- **Phase Number**: Phase 12
- **Objective**: Generate type-safe Java 21 model records, view records, input DTOs, filter criteria, and the fluent `GraphOrmClient` using JavaPoet.
- **Why Necessary**: Delivers the Prisma-style developer ergonomics for Java developers with full compile-time safety and IDE auto-completion (ADR-012).
- **Expected Outcome**: Clean, idiomatic Java 21 source code that compiles without warnings and provides fluent client APIs (`db.student().findMany(...)`).

### 12.2 Technical Breakdown
- **Modules Involved**: `graphorm-generator`, `graphorm-core`, `graphorm-jdbc`
- **Functionality**:
  - Model Record generator (Java 21 records).
  - View Model Record generator (read-only projection records).
  - Composite Type Record generator.
  - Input DTO generator (`CreateInput`, `UpdateInput`) for regular models only.
  - Filter criteria builders for models and views.
  - `GraphOrmClient` generator: `db.student()`, `db.studentDashboard()`. View endpoints omit write methods.
  - In-memory `JavaCompiler` test harness (JSR-199).
- **Dependencies**: Phase 11.

### 12.3 Implementation Tasks
- **TASK-12.1**: Implement `ModelRecordGenerator` using JavaPoet.
- **TASK-12.2**: Implement `ViewModelRecordGenerator` (read-only records).
- **TASK-12.3**: Implement `InputDtoGenerator` (strictly for regular models).
- **TASK-12.4**: Implement `FilterCriteriaGenerator`.
- **TASK-12.5**: Implement `ClientApiGenerator` for `GraphOrmClient`.
- **TASK-12.6**: In-memory compilation test suite using `javax.tools.JavaCompiler`.

### 12.4 Milestone Deliverable: **Milestone 6**
- Generate complete Java sources from schema, compile them cleanly in test harness, and execute `db.student().findMany()` and `db.studentDashboard().findMany()`.

---

## Phase 13: Spring Boot Starter & Auto-Configuration (`graphorm-spring-boot-starter`)

### 13.1 Phase Overview
- **Phase Number**: Phase 13
- **Objective**: Build `graphorm-spring-boot-starter` with auto-configuration, DataSource binding, `@Transactional` integration, and Spring for GraphQL auto-wiring.
- **Why Necessary**: Enables Spring Boot developers to include a single starter dependency and have GraphORM fully configured with zero manual boilerplate (ADR-013, ADR-014).
- **Expected Outcome**: Seamless Spring Boot 3+ starter that automatically configures GraphORM beans, registers DataFetchers, and binds to Spring transactions.

### 13.2 Technical Breakdown
- **Modules Involved**: `graphorm-spring-boot-starter`, `graphorm-graphql`, `graphorm-jdbc`
- **Functionality**:
  - `GraphOrmAutoConfiguration` registered via `AutoConfiguration.imports`.
  - `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`.
  - Auto-discovery of `schema.graphorm` on classpath.
  - `GraphOrmProperties` (`graphorm.schema-location`, `graphorm.sql-logging`, `graphorm.batch-size`).
  - Spring `DataSourceUtils` integration for `@Transactional`.
  - `GraphQlSourceBuilderCustomizer` for Spring for GraphQL.
- **Dependencies**: Phase 11, Phase 12.

### 13.3 Implementation Tasks
- **TASK-13.1**: Create `GraphOrmProperties` with configuration metadata.
- **TASK-13.2**: Implement `SpringTransactionManagerAdapter` delegating to Spring's `DataSourceUtils`.
- **TASK-13.3**: Implement `GraphOrmAutoConfiguration`.
- **TASK-13.4**: Implement `GraphQlAutoConfigurationCustomizer`.
- **TASK-13.5**: Test using `ApplicationContextRunner` verifying bean creation and conditional rollbacks.

### 13.4 Milestone Deliverable: **Milestone 7**
- Boot a Spring Boot test application with the starter; verify `GraphOrmClient` is injected, transactions commit/rollback with `@Transactional`, and GraphQL endpoints respond.

---

## Phase 14: Developer CLI Tool (`graphorm-cli`)

### 14.1 Phase Overview
- **Phase Number**: Phase 14
- **Objective**: Implement the command-line interface for project initialization, schema validation, code generation, and database introspection.
- **Why Necessary**: Provides developer tooling outside of the build lifecycle.
- **Expected Outcome**: An executable CLI with commands: `graphorm init`, `graphorm validate`, `graphorm generate`, `graphorm introspect`.

### 14.2 Technical Breakdown
- **Modules Involved**: `graphorm-cli`, `graphorm-schema`, `graphorm-generator`, `graphorm-jdbc`
- **Functionality**:
  - Picocli command framework.
  - Commands: `init`, `validate`, `generate`, `introspect`.
  - Database introspection: read PostgreSQL catalog tables (`pg_class`, `pg_attribute`, `information_schema.views`) and emit initial `schema.graphorm`.
- **Dependencies**: Phase 12.

### 14.3 Implementation Tasks
- **TASK-14.1**: Set up Picocli runner and help documentation.
- **TASK-14.2**: Implement `graphorm validate` command.
- **TASK-14.3**: Implement `graphorm generate` command.
- **TASK-14.4**: Implement `graphorm init` command.
- **TASK-14.5**: Implement `graphorm introspect` reading PostgreSQL tables and views.
- **TASK-14.6**: CLI end-to-end integration tests.

---

## Phase 15: Security, Performance & Production Readiness

### 15.1 Phase Overview
- **Phase Number**: Phase 15
- **Objective**: Implement GraphQL complexity and depth limits, field-level authorization (`@auth`), connection leak safeguards, query caching, and Micrometer observability.
- **Why Necessary**: Protects applications against DoS attacks, unauthorized data access, and provides production monitoring (ADR-015).
- **Expected Outcome**: Hardened production framework with metrics, security interceptors, and error sanitization.

### 15.2 Technical Breakdown
- **Modules Involved**: `graphorm-graphql`, `graphorm-jdbc`, `graphorm-spring-boot-starter`
- **Functionality**:
  - `MaxQueryDepthInstrumentation` and `MaxQueryComplexityInstrumentation`.
  - Field-level authorization via `@auth` and Spring Security context.
  - Sanitized error handler mapping database exceptions to client-safe codes.
  - Micrometer timers and counters for query execution times and DataLoader batches.
- **Dependencies**: Phase 13.

### 15.3 Implementation Tasks
- **TASK-15.1**: Implement GraphQL depth and complexity limit instrumentation.
- **TASK-15.2**: Implement field-level security interceptor with Spring Security.
- **TASK-15.3**: Implement `GraphOrmExceptionHandler` with sanitized error codes.
- **TASK-15.4**: Add Micrometer metrics recording query duration and batch loader sizes.
- **TASK-15.5**: Security test suite verifying DoS attack prevention and access control.

### 15.4 Milestone Deliverable: **Milestone 8**
- Execute a query exceeding depth 8 (rejected with 400 Bad Request); query `@auth(roles: ["ADMIN"])` as regular user (rejected with 403 Forbidden); assert query execution metrics recorded in Micrometer registry.

---

## Phase 16: Documentation, End-to-End Examples & Release Preparation

### 16.1 Phase Overview
- **Phase Number**: Phase 16
- **Objective**: Build a complete reference application (`graphorm-examples/spring-boot-postgres`), comprehensive developer documentation, quickstart guides, and prepare for v1.0 release.
- **Why Necessary**: Provides practical real-world validation and documentation for developer adoption.
- **Expected Outcome**: A fully functional reference application demonstrating models, database views, virtual views, GraphQL queries, and type-safe client APIs.

### 16.2 Technical Breakdown
- **Modules Involved**: `graphorm-examples/spring-boot-postgres`, `docs/*`
- **Functionality**:
  - Complete university domain sample: `Student`, `Department`, `Course`, `Enrollment`, `Instructor`.
  - Views: `StudentDashboard` (virtual view with joins & aggregations), `DepartmentStats` (database-backed view).
  - GraphiQL UI integration for browser testing.
  - Comprehensive documentation and getting-started tutorials.
- **Dependencies**: Phase 14, Phase 15.

### 16.3 Implementation Tasks
- **TASK-16.1**: Build `spring-boot-postgres` example application with schemas, Flyway migrations, and data seeders.
- **TASK-16.2**: Write end-to-end integration tests covering all sample queries and mutations.
- **TASK-16.3**: Write comprehensive Developer Guide and Quickstart in `docs/`.
- **TASK-16.4**: Final verification of all documentation, skills, and Definition of Done checklists.
- **Completion Criteria**: Clean build, 100% tests passing, runnable reference application, and complete documentation.
