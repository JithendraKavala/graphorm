# GraphORM: Comprehensive Developer Guide

A deep dive into GraphORM architecture, data modeling, query engine, code generation, Spring Boot integration, and production best practices.

---

## 1. Architectural Philosophy & Mental Model

GraphORM is a **hybrid schema-first ORM and automatic GraphQL engine** designed specifically for Java 21+. It eliminates the traditional object-relational impedance mismatch by treating schemas as the single source of truth and compiling queries into minimal, parameterized SQL ASTs without ORM entity caching or dirty-tracking overhead.

### Key Architectural Pillars
- **Zero Foreign Dependencies in Core**: The core metadata model, schema AST, and query AST live in `graphorm-core` with zero Spring, Hibernate, or database driver dependencies.
- **Strict Immutability**: All parsed schema nodes and runtime metadata objects (`ModelRegistry`, `ModelMetadata`, `ViewModelMetadata`) are 100% immutable once constructed.
- **Unified Query AST**: Regular models (`model`) and view models (`view`) share the same unified `SelectQuery` AST, SQL dialect generators, and execution pipelines.
- **Strict Read-Only Invariants**: View models are strictly read-only. GraphORM guarantees at compile time and runtime that view models cannot have `create`, `update`, or `delete` operations or input DTOs.
- **Selection-Set Projection Pruning**: GraphQL queries automatically prune unrequested SQL columns, eliminating over-fetching while safely injecting hidden foreign keys for downstream relationship resolution.
- **N+1 Query Elimination**: All 1:1, 1:N, and N:1 relations are resolved via batched `WHERE foreign_key IN (?, ?, ...)` queries using GraphQL Java DataLoaders.

```
+-----------------------------------------------------------------------+
|                           schema.graphorm                             |
+-----------------------------------+-----------------------------------+
                                    |
          +-------------------------+-------------------------+
          v                                                   v
+------------------------+                         +--------------------+
|  graphorm-generator    |                         |  graphorm-schema   |
|  JavaPoet Generators   |                         |  ANTLR4 Parser     |
+-----------+------------+                         +----------+---------+
            |                                                 |
            v                                                 v
+------------------------+                         +--------------------+
| Immutable Java Records |                         |   ModelRegistry    |
| & Type-Safe Client API |                         +----------+---------+
+-----------+------------+                                    |
            |                         +-----------------------+
            v                         v
+-----------------------------------------------------------------------+
|                             graphorm-jdbc                             |
|       Unified Query AST, SQL Dialects, Dynamic Row Mapper, Tx         |
+-------------------------------------+---------------------------------+
                                      |
                                      v
+-----------------------------------------------------------------------+
|                           graphorm-graphql                            |
|    Auto DataFetchers, DataLoaders, Selection Pruning, @auth Security  |
+-------------------------------------+---------------------------------+
                                      |
                                      v
+-----------------------------------------------------------------------+
|                     graphorm-spring-boot-starter                      |
|           Auto-Configuration, DataSource, Transaction Manager         |
+-----------------------------------------------------------------------+
```

---

## 2. Schema Modeling

GraphORM schemas use a Prisma-inspired declarative syntax (`schema.graphorm`).

### 2.1 Models & Primary Keys
```prisma
model Department {
  id       Int       @id @default(autoincrement())
  name     String    @map("dept_name")
  code     String    @unique
  students Student[]

  @@map("departments")
}
```

### 2.2 Relationships
Relationships are defined using `@relation(fields: [...], references: [...])`:

```prisma
model Student {
  id           Int         @id @default(autoincrement())
  name         String
  email        String      @unique
  departmentId Int         @map("department_id")
  department   Department? @relation(fields: [departmentId], references: [id])
  enrollments  Enrollment[]

  @@map("students")
}
```

Supported relationship cardinalities:
- **Many-to-One (N:1)**: Child entity references parent entity via foreign key field.
- **One-to-Many (1:N)**: Parent entity declares collection of child entities (`Student[]`).
- **One-to-One (1:1)**: Singular reference with unique constraint on the foreign key field.

---

## 3. View Models

GraphORM introduces first-class View Models (`view`), categorized into two stages:

### 3.1 Stage 1: Database-Backed Physical Views (`@@view`)
Maps directly to an existing database view or materialized view created in PostgreSQL:

```prisma
view DepartmentStats {
  id             Int    @id
  departmentName String @map("department_name")
  studentCount   Int    @map("student_count")
  courseCount    Int    @map("course_count")

  @@view
  @@map("v_department_stats")
}
```

### 3.2 Stage 2: Virtual Schema-Defined Views
Virtual views are generated dynamically in SQL without requiring any database DDL view. They support multi-table joins, aggregations, computed expressions, and group-by columns:

```prisma
view StudentDashboard {
  studentId       Int     @map("s.id")
  studentName     String  @map("s.name")
  departmentName  String? @map("d.dept_name")
  enrolledCourses Int     @aggregate(count: "e.id")
  avgGradePoints  Float?  @expression("ROUND(AVG(e.grade_points)::numeric, 2)")

  @@from(Student, as: "s")
  @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
  @@leftJoin(Enrollment, as: "e", on: "s.id == e.studentId")
  @@groupBy(["s.id", "s.name", "d.dept_name"])
}
```

#### Join Pruning Optimization
When querying `StudentDashboard`, if client selections do not request `enrolledCourses` or `avgGradePoints`, the `Enrollment` join is **automatically pruned** from the generated SQL, saving execution time and database IO:

```sql
-- Client requests only studentId and departmentName:
SELECT "s"."id" AS "studentId", "d"."dept_name" AS "departmentName"
FROM "students" AS "s"
LEFT JOIN "departments" AS "d" ON "s"."department_id" = "d"."id"
```

---

## 4. Type-Safe Java Client API

The `GraphOrmClient` generated by `graphorm-generator` provides a clean, Prisma-style fluent database client.

### 4.1 Queries & Filtering
```java
// Query multiple with filters, ordering, and pagination
List<Student> students = db.student().findMany(q -> q
    .where(f -> f.departmentId().eq(1).and(f.gpa().gte(3.5)))
    .orderBy(f -> f.name().asc())
    .limit(25)
    .offset(0)
);

// Find unique by ID
Optional<Student> student = db.student().findById(101);

// Count matching records
long count = db.student().count(q -> q.where(f -> f.departmentId().eq(1)));

// Check existence
boolean exists = db.student().exists(q -> q.where(f -> f.email().eq("ada@lovelace.edu")));
```

### 4.2 Mutations (Regular Models Only)
```java
// Create
Student created = db.student().create(StudentCreateInput.builder()
    .name("Grace Hopper")
    .email("hopper@navy.mil")
    .departmentId(1)
    .build());

// Update
Student updated = db.student().update(created.id(), StudentUpdateInput.builder()
    .gpa(4.0)
    .build());

// Delete
boolean deleted = db.student().delete(created.id());
```

### 4.3 View Queries (Strictly Read-Only)
View clients provide `findMany`, `findFirst`, `findById`, and `count`. They do **not** expose `create`, `update`, or `delete`:

```java
List<StudentDashboard> dashboards = db.studentDashboard().findMany(q -> q
    .where(f -> f.enrolledCourses().gte(2))
    .orderBy(f -> f.avgGradePoints().desc())
);
```

### 4.4 Transactions
GraphORM integrates seamlessly with Spring's `@Transactional` and provides programmatic transaction boundaries:

```java
transactionManager.executeInTransaction(() -> {
    Student student = db.student().create(studentInput);
    db.enrollment().create(enrollmentInput.withStudentId(student.id()));
    return student;
});
```

---

## 5. Automatic GraphQL Integration

When `graphorm-spring-boot-starter` is on the classpath, GraphQL queries and mutations are automatically registered with zero boilerplate.

### 5.1 Nested Query Resolution with DataLoader Batching
```graphql
query {
  students {
    id
    name
    department {
      name
    }
  }
}
```
**Execution Plan (Exact Query Counts):**
1. **Query 1**: `SELECT "id", "name", "department_id" AS "departmentId" FROM "students"`
2. **Query 2**: `SELECT "id", "dept_name" AS "name" FROM "departments" WHERE "id" IN (1, 2)`

### 5.2 Selection Set Column Pruning
If a GraphQL client queries only `{ students { name } }`, GraphORM does **not** execute `SELECT *`. It issues:
```sql
SELECT "name" FROM "students"
```
If a downstream relationship is requested (e.g. `department`), GraphORM injects `department_id` as a **hidden column** (`isHidden=true`), uses it to batch-load the relation, and strips it before writing the JSON response.

---

## 6. Security & Production Hardening

### 6.1 Field-Level Authorization (`@auth`)
Attach `@auth(roles: [...])` to sensitive fields:

```prisma
model Instructor {
  id     Int     @id @default(autoincrement())
  name   String
  salary Float?  @auth(roles: ["ADMIN", "HR"])
}
```
Unauthenticated or unauthorized callers receive an `ACCESS_DENIED` GraphQL error:
```json
{
  "errors": [
    {
      "message": "Access denied to field 'salary' on 'Instructor'. Required roles: [ADMIN, HR]",
      "extensions": {
        "code": "ACCESS_DENIED"
      }
    }
  ]
}
```

### 6.2 Denial-of-Service (DoS) Protection
- **Max Query Depth**: Rejects maliciously nested queries exceeding `graphorm.security.max-query-depth`.
- **Max Query Complexity**: Assigns complexity points to fields and batch operations, rejecting queries that exceed `graphorm.security.max-query-complexity`.

### 6.3 SQL Injection Immunity
100% of user inputs and filter criteria are bound as positional parameter markers (`?`). All table names, view names, column names, and aliases are verified against `ModelRegistry` and quoted using dialect identifier escaping.

---

## 7. Metrics & Observability

GraphORM natively instruments performance metrics using Micrometer:

| Metric Name | Type | Description |
|---|---|---|
| `graphorm.query.duration` | Timer | JDBC query execution time categorized by operation and table |
| `graphorm.dataloader.batch.size` | DistributionSummary | Size of batched `IN` query lookups |
| `graphorm.dataloader.batch.duration` | Timer | Total resolution time of DataLoader batch operations |

Enable SQL logging in development:
```yaml
graphorm:
  sql-logging: true
```
Logs executed SQL along with bound parameters:
```
INFO  com.graphorm.sql : SQL: SELECT "id", "name" FROM "students" WHERE "department_id" IN (?) | params: [1]
DEBUG com.graphorm.sql : SQL executed in 0.85 ms
```
