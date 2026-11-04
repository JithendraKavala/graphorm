# GraphORM: Schema Language Specification

## 1. Overview

GraphORM uses a declarative schema language inspired by Prisma to define data models, views, composite types, fields, and relationships. The schema file is conventionally named `schema.graphorm`.

## 2. Model Categories

The schema engine strictly categorizes declarations into three types:

### 2.1 Regular Models (`model`)
Regular models represent physical database tables and support full CRUD operations.
```prisma
model Student {
    id           Int         @id @default(autoincrement())
    name         String
    email        String      @unique
    departmentId Int?
    department   Department? @relation(fields: [departmentId], references: [id])
    enrollments  Enrollment[]

    @@map("students")
    @@index([departmentId])
}
```

### 2.2 View Models (`view`)
View models represent **read-only** data projections. They combine multiple models, joins, aggregations, and computed expressions into one queryable entity.

#### Strategy A: Database-Backed Views
Maps directly to an existing database view or materialized view in PostgreSQL.
```prisma
view StudentSummary {
    id             Int      @id
    name           String
    departmentName String   @map("dept_name")
    totalCredits   Int      @map("total_credits")

    @@view
    @@map("vw_student_summary")
}
```

#### Strategy B: GraphORM-Defined Views
Declared virtually in the schema without creating a physical database view. The query engine generates dynamic SQL (joins, subqueries, CTEs, aggregations).
```prisma
view StudentDashboard {
    studentId      Int                 @map("s.id")
    studentName    String              @map("s.name")
    departmentName String?             @map("d.name")
    courseCount    Int                 @aggregate(count: "e.id")
    gpa            Float?              @expression("AVG(e.grade_points)")
    primaryAddress Address?

    // Declarative source and join definitions
    @@from(Student, as: "s")
    @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
    @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
    @@groupBy(["s.id", "s.name", "d.name"])
}
```

### 2.3 Composite Types (`type`)
Composite types represent reusable nested data structures that can be embedded into models or view models. They do not have independent database tables or `@id` requirements.
```prisma
type Address {
    street  String
    city    String
    state   String
    zipCode String
}
```

## 3. Supported Scalar Types

| Schema Type | Java Type | PostgreSQL Type |
|---|---|---|
| `Int` | `Integer` | `INTEGER` |
| `BigInt` | `Long` | `BIGINT` |
| `String` | `String` | `VARCHAR` / `TEXT` |
| `Boolean` | `Boolean` | `BOOLEAN` |
| `Float` | `Double` | `DOUBLE PRECISION` |
| `DateTime` | `Instant` / `LocalDateTime` | `TIMESTAMP WITH TIME ZONE` |
| `Json` | `String` / Jackson `JsonNode` | `JSONB` |
| `Bytes` | `byte[]` | `BYTEA` |

## 4. Modifiers
- `?`: Optional / Nullable field (e.g. `String?`, `Int?`).
- `[]`: List field (e.g. `Student[]`, `Enrollment[]`).

## 5. Field Attributes

### Model & View Attributes
- `@id`: Marks the primary key or unique identifier field (required on `model`, required on database-backed `view` if identifiable).
- `@default(value)`: Sets a default value (`autoincrement()`, `now()`, literal) - regular models only.
- `@unique`: Creates a unique constraint on the column - regular models only.
- `@map("column_name" | "alias.column")`: Maps the field to a database column name or qualified join source column.
- `@relation(fields: [...], references: [...])`: Defines relationship foreign key mappings.

### View-Specific Field Attributes
- `@aggregate(count | sum | avg | min | max: "expression")`: Marks an aggregated metric field calculated in SQL.
- `@expression("SQL expression")`: Marks a computed field calculated directly by the database engine.

## 6. Block Attributes

- `@@id([...])`: Compound primary key (models only).
- `@@unique([...])`: Compound unique constraint (models only).
- `@@index([...])`: Database index (models only).
- `@@map("table_or_view_name")`: Maps the model or view to a physical table/view name.
- `@@view`: Explicitly denotes a database-backed view or materialized view.
- `@@from(SourceModel, as: "alias")`: Defines the primary source model for a GraphORM-defined view.
- `@@leftJoin(Model, as: "alias", on: "join_condition")`: Declares a join on a source model.
- `@@innerJoin(Model, as: "alias", on: "join_condition")`: Declares an inner join on a source model.
- `@@groupBy(["alias.field", ...])`: Specifies grouping columns for aggregated views.

## 7. Semantic Validation Rules for Views
1. **Read-Only Invariant**: View models cannot contain `@default(autoincrement())` write defaults.
2. **Source Model Validation**: All models referenced in `@@from`, `@@leftJoin`, and `@@innerJoin` must exist in `ModelRegistry`.
3. **Join Predicate Integrity**: Join predicates must reference valid fields on the respective source models.
4. **No Write Mutation Generation**: The schema validator flags an error if a mutation is declared for a view model.
5. **No Circular View Dependencies**: Reusable views cannot reference each other in a cyclic dependency chain.
