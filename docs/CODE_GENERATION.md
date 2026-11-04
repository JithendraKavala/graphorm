# GraphORM: Code Generation Engine Design

## 1. Objectives

The code generator (`graphorm-generator`) translates `schema.graphorm` into strongly-typed Java artifacts:
1. **Model Records/Classes**: Represents database rows with full CRUD capabilities.
2. **View Model Records**: Represents read-only multi-table projections, aggregations, and computed views.
3. **Composite Type Records**: Reusable nested value objects (e.g. `Address`).
4. **Create / Update Input DTOs**: Encapsulates mutation payloads (generated exclusively for regular models).
5. **Filter Criteria Classes**: Type-safe criteria builders for querying models and views.
6. **Model & View Metadata Classes**: Static metadata descriptors for runtime lookup without reflection.
7. **Database Client (`GraphOrmClient`)**: Fluent Prisma-style API for all models and views.

## 2. Output Structure

Given model `Student` and view `StudentDashboard`:

```java
// 1. Regular Model Record (CRUD)
public record Student(
    Integer id,
    String name,
    String email,
    Integer departmentId
) {}

// 2. View Model Record (Read-Only)
public record StudentDashboard(
    Integer studentId,
    String studentName,
    String departmentName,
    Integer courseCount,
    Double gpa,
    Address primaryAddress
) {}

// 3. Composite Type Record
public record Address(
    String street,
    String city,
    String state,
    String zipCode
) {}

// 4. Create Input (Generated only for regular models, NEVER for views)
public record StudentCreateInput(
    String name,
    String email,
    Integer departmentId
) {
    public static Builder builder() { return new Builder(); }
}

// 5. Filter Criteria (Generated for both models and views)
public class StudentDashboardFilter {
    public static class StringFilter {
        public StudentDashboardFilter eq(String value) { ... }
        public StudentDashboardFilter contains(String value) { ... }
    }
}

// 6. Metadata Descriptor
public final class StudentDashboardMetadata implements ViewModelMetadata {
    public static final String VIEW_NAME = "StudentDashboard";
    public static final FieldMetadata STUDENT_ID = new FieldMetadata("studentId", Integer.class, true);
    public static final FieldMetadata COURSE_COUNT = new FieldMetadata("courseCount", Integer.class, false);
}
```

## 3. Client API Contract

The generated client provides fluent, type-safe database access with strict read-only enforcement on views:

```java
GraphOrmClient db = ...;

// Regular Model: findMany with filter and pagination
List<Student> students = db.student().findMany(q -> q
    .where(s -> s.departmentId().eq(10))
    .orderBy(s -> s.name().asc())
    .limit(20)
);

// Regular Model: create
Student created = db.student().create(StudentCreateInput.builder()
    .name("Alice")
    .email("alice@example.com")
    .departmentId(10)
    .build()
);

// View Model: findMany with joins/aggregations pushed down to PostgreSQL
List<StudentDashboard> dashboards = db.studentDashboard().findMany(q -> q
    .where(d -> d.courseCount().gte(3))
    .orderBy(d -> d.gpa().desc())
    .limit(10)
);

// View Model: findUnique
Optional<StudentDashboard> dashboard = db.studentDashboard().findUnique(q -> q
    .where(d -> d.studentId().eq(42))
);

// Note: db.studentDashboard().create(...) or update(...) DOES NOT EXIST (Read-Only enforced)
```

## 4. Execution Integration

The generator will be invokable via:
1. **GraphORM CLI**: `graphorm generate --schema=schema.graphorm`
2. **Maven Plugin**: Executed automatically during the `generate-sources` phase.
