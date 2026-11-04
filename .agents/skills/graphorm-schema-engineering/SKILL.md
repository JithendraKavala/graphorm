---
name: graphorm-schema-engineering
description: >-
  Guides the development of the Prisma-inspired schema language, covering tokenization, parsing,
  AST design, model categories (models, views, composite types), attributes/directives, relationship definitions,
  schema validation, and error reporting. Use when modifying grammar, parser rules, AST models, or validator diagnostics in graphorm-schema.
---

# GraphORM Schema Engineering

This skill defines the principles, architecture, and implementation details for the Prisma-inspired schema parser in `graphorm-schema`.

For implementation roadmap and phase specifications, see:
- [PHASE_BREAKDOWN.md - Phase 3](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [SCHEMA_LANGUAGE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/SCHEMA_LANGUAGE.md)
- [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)

---

## Model Categories

The schema engine must distinguish between three distinct model categories:
1. **Regular Models (`model`)**: Represent physical database tables; support CRUD operations, primary keys, foreign keys, and indexes.
2. **View Models (`view`)**: Represent read-only data projections combining multiple models, relationships, aggregations, and computed expressions.
   - **Database-Backed Views**: Annotated with `@@view` and `@@map("db_view_name")`.
   - **GraphORM-Defined Views**: Virtual views declared with `@@from`, `@@leftJoin`, `@@innerJoin`, and `@@groupBy`.
3. **Composite Types (`type`)**: Reusable nested structures (e.g. `Address`, `Money`) embedded in models or views without independent tables.

## Schema Syntax Specification

```prisma
datasource db {
    provider = "postgresql"
}

// 1. Regular Model (CRUD)
model Student {
    id           Int         @id @default(autoincrement())
    name         String
    email        String      @unique
    departmentId Int?
    department   Department? @relation(fields: [departmentId], references: [id])
    enrollments  Enrollment[]
}

model Department {
    id       Int       @id @default(autoincrement())
    name     String
    students Student[]
}

// 2. View Model (Read-Only Projection)
view StudentDashboard {
    studentId      Int     @map("s.id")
    studentName    String  @map("s.name")
    departmentName String? @map("d.name")
    courseCount    Int     @aggregate(count: "e.id")
    gpa            Float?  @expression("AVG(e.grade_points)")
    primaryAddress Address?

    @@from(Student, as: "s")
    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
    @@groupBy(["s.id", "s.name", "d.name"])
}

// 3. Composite Type
type Address {
    street String
    city   String
    state  String
}
```

## Lexer & Parser Architecture

1. **Tokenization (Lexer)**:
   - Recognizes tokens: `MODEL`, `VIEW`, `TYPE`, `ENUM`, `DATASOURCE`, `GENERATOR`, `IDENTIFIER`, `AT` (`@`), `DOUBLE_AT` (`@@`), `LBRACE`, `RBRACE`, `LPAREN`, `RPAREN`, `LBRACKET`, `RBRACKET`, `COLON`, `QUESTION`, `STRING_LITERAL`, `INT_LITERAL`.
   - Tracks line and column numbers on every token for precise diagnostics.
2. **Parser**:
   - Parses top-level blocks: `datasource`, `generator`, `model`, `view`, `type`, `enum`.
   - Parses field declarations: `<name> <type>[?|[]] [@attributes...]`.
   - Parses view attributes: `@@from(...)`, `@@leftJoin(...)`, `@@innerJoin(...)`, `@@groupBy(...)`, `@aggregate(...)`, `@expression(...)`.
3. **AST Design (`graphorm-schema` AST)**:
   - `SchemaDocument(List<BlockNode> blocks)`
   - `ModelNode(String name, List<FieldNode> fields, List<BlockAttributeNode> attributes)`
   - `ViewModelNode(String name, boolean isDatabaseBacked, List<FieldNode> fields, List<BlockAttributeNode> attributes)`
   - `CompositeTypeNode(String name, List<FieldNode> fields)`
   - `FieldNode(String name, TypeNode type, boolean isOptional, boolean isList, List<FieldAttributeNode> attributes)`
   - `FieldAttributeNode(String name, List<ArgumentNode> arguments)`

## Validation Engine

The validator must run semantic passes over the AST:
1. **Type Resolution**: Ensure all field types are built-in scalars (`Int`, `String`, `Boolean`, `Float`, `DateTime`, `Json`, `BigInt`, `Bytes`), defined models, views, composite types, or enums.
2. **Primary Key Requirement**: Every `model` must have exactly one `@id` field or one `@@id(...)` compound key. Database-backed `view`s should declare an `@id` if individual row retrieval is required.
3. **View Source & Join Validation**:
   - Verify that models referenced in `@@from`, `@@leftJoin`, and `@@innerJoin` exist in the schema.
   - Verify that fields referenced in join predicates exist on the respective source models.
   - Verify that grouped/aggregated fields conform to SQL semantic requirements.
4. **Read-Only Invariant Enforcement**:
   - Reject write defaults (`@default(autoincrement())`) and write-only annotations on view models.
5. **Cyclic View Reference Detection**:
   - Construct a directed dependency graph of view source references; emit diagnostics if a cycle is detected.
6. **Relation Consistency on Models**:
   - Both sides of a model relation must be declared.
   - The owner side must specify `@relation(fields: [...], references: [...])`.
7. **Diagnostic Reporting**:
   - Collect all errors with source coordinates:
     `[ERROR] schema.graphorm:15:5 - View 'StudentDashboard' references unknown source model 'NonExistentModel'`
