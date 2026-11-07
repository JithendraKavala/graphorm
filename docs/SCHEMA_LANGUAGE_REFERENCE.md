# GraphORM Schema Language Reference

This document provides the complete syntax specification for the Prisma-inspired GraphORM Schema Language (`schema.graphorm`).

---

## 1. Top-Level Declarations

A `.graphorm` schema file consists of declarations at the file level:
1. `datasource`: Configures the database connection.
2. `generator`: Configures code generation options.
3. `model`: Defines a persistent database table and its relations.
4. `view`: Defines a database-backed or virtual queryable view model.
5. `type`: Defines an embedded composite value object.
6. `enum`: Defines a strongly typed enumerated set.

---

## 2. Datasource Configuration

```prisma
datasource db {
  provider = "postgresql"
  url      = env("DATABASE_URL")
}
```

### Properties
- `provider`: Target SQL dialect (`"postgresql"`).
- `url`: Connection URL string or `env("VAR_NAME")` reference.

---

## 3. Generator Configuration

```prisma
generator client {
  provider = "graphorm-java"
  output   = "src/main/java"
  package  = "com.example.generated"
}
```

### Properties
- `provider`: Code generator engine (`"graphorm-java"`).
- `output`: Output directory where Java packages will be created.
- `package`: Base Java package name for generated artifacts.

---

## 4. Scalar Data Types

GraphORM maps schema types to Java 21 types and SQL column types:

| Schema Type | Java Type | PostgreSQL Type | Notes |
|---|---|---|---|
| `Int` | `Integer` / `int` | `INTEGER` | 32-bit signed integer |
| `BigInt` | `Long` / `long` | `BIGINT` | 64-bit signed integer |
| `Float` | `Double` / `double` | `DOUBLE PRECISION` | 64-bit IEEE floating point |
| `Decimal` | `BigDecimal` | `NUMERIC` | Arbitrary precision decimal |
| `String` | `String` | `VARCHAR` / `TEXT` | UTF-8 text string |
| `Boolean` | `Boolean` / `boolean` | `BOOLEAN` | True or false |
| `DateTime` | `Instant` / `LocalDateTime` | `TIMESTAMP WITH TIME ZONE` | ISO-8601 timestamp |
| `Json` | `String` / `JsonNode` | `JSONB` | Structured JSON document |

### Type Modifiers
- `Type?`: Nullable field (optional).
- `Type[]`: List / array of elements (used for 1:N relations or list types).

---

## 5. Field Directives

Directives modify fields or attach constraints and behaviors:

### `@id`
Marks the field as the primary key.
```prisma
id Int @id
```

### `@default(...)`
Assigns a default value generator or constant.
```prisma
id        Int      @id @default(autoincrement())
createdAt DateTime @default(now())
isActive  Boolean  @default(true)
```

### `@unique`
Declares a unique constraint on the field.
```prisma
email String @unique
```

### `@map("column_name")`
Maps the schema field name to a different physical database column name.
```prisma
departmentId Int @map("department_id")
```

### `@relation(fields: [...], references: [...])`
Declares the foreign key relationship between models.
```prisma
departmentId Int        @map("department_id")
department   Department @relation(fields: [departmentId], references: [id])
```

### `@auth(roles: [...])`
Restricts access to the field to callers possessing one of the specified roles.
```prisma
salary Float? @auth(roles: ["ADMIN", "MANAGER"])
```

### `@aggregate(...)` (View Models Only)
Applies a SQL aggregation function to the target column or expression.
```prisma
studentCount Int @aggregate(count: "s.id")
totalRevenue Float @aggregate(sum: "o.amount")
avgGpa       Float @aggregate(avg: "e.grade_points")
minPrice     Float @aggregate(min: "p.price")
maxPrice     Float @aggregate(max: "p.price")
```

### `@expression("...")` (View Models Only)
Declares a raw SQL computed expression.
```prisma
gpa Float? @expression("ROUND(AVG(e.grade_points)::numeric, 2)")
```

---

## 6. Block Attributes

Block attributes apply to the entire model or view:

### `@@map("table_name")`
Maps the model or view to a physical database table or view name.
```prisma
model Student {
  ...
  @@map("students")
}
```

### `@@view` (View Models Only)
Declares that the view is a **Stage 1 Database-Backed Physical View**, querying an existing database view.
```prisma
view DepartmentStats {
  id             Int    @id
  departmentName String @map("department_name")
  studentCount   Int    @map("student_count")

  @@view
  @@map("v_department_stats")
}
```

### `@@from(ModelName, as: "alias")` (Stage 2 Virtual Views Only)
Specifies the primary source entity and its table alias.
```prisma
@@from(Student, as: "s")
```

### `@@leftJoin(TargetModel, as: "alias", on: "condition")` (Stage 2 Virtual Views Only)
Specifies a `LEFT OUTER JOIN` against a target model with join condition.
```prisma
@@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
```

### `@@innerJoin(TargetModel, as: "alias", on: "condition")` (Stage 2 Virtual Views Only)
Specifies an `INNER JOIN` against a target model.
```prisma
@@innerJoin(Enrollment, as: "e", on: "s.id == e.studentId")
```

### `@@groupBy(["alias.field", ...])` (Stage 2 Virtual Views Only)
Specifies the columns for `GROUP BY` aggregation.
```prisma
@@groupBy(["s.id", "s.name", "d.dept_name"])
```

---

## 7. Composite Types & Enums

### Composite Types
Embedded value objects without an independent lifecycle or identity:
```prisma
type Address {
  street  String
  city    String
  zipCode String @map("zip_code")
}

model Student {
  id      Int     @id @default(autoincrement())
  name    String
  address Address
}
```

### Enums
Strongly typed enumerations:
```prisma
enum EnrollmentStatus {
  PENDING
  ACTIVE
  COMPLETED
  DROPPED
}

model Enrollment {
  id     Int              @id @default(autoincrement())
  status EnrollmentStatus @default(PENDING)
}
```

---

## 8. Validation Diagnostics & Error Format

The GraphORM validator verifies semantic invariants before code generation or metadata initialization:
- Every `model` must have exactly one `@id` primary key.
- `@relation` foreign keys must match target types and fields.
- Circular non-nullable relations are rejected.
- Views are strictly prohibited from declaring write mutations or `@default(autoincrement())`.
- Join aliases in virtual views must be unique and defined in `@@from` or `@@*Join`.
- Diagnostics display file name, line, column, and caret pointer:
```
[ERROR] schema.graphorm:14:3 - Model 'Student' cannot declare @aggregate on field 'courseCount'. Aggregations are only permitted on view models.
  14 |   courseCount Int @aggregate(count: "id")
     |   ^^^^^^^^^^^
```
