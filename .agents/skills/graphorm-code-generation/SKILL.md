---
name: graphorm-code-generation
description: >-
  Guides automatic Java source code generation from GraphORM schemas. Covers Java model records/classes,
  read-only view model records, composite types, create/update inputs, filter types, type-safe database client APIs,
  and metadata classes. Use when working on graphorm-generator or CLI code generation commands.
---

# GraphORM Java Code Generation

This skill governs the code generation engine in `graphorm-generator`.

For implementation roadmap and phase specifications, see:
- [PHASE_BREAKDOWN.md - Phase 12](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [CODE_GENERATION.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/CODE_GENERATION.md)
- [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)

---

## Generated Artifacts

From a schema containing models, views, and composite types, GraphORM generates:
1. **Regular Entity Models**: Immutable Java records or POJOs representing physical database rows.
2. **View Model Records**: Immutable Java records representing read-only multi-table projections, aggregations, and computed views.
3. **Composite Types**: Reusable immutable Java records (e.g. `Address`).
4. **Input Types**: `StudentCreateInput`, `StudentUpdateInput` (generated **exclusively** for regular models; **never** for views).
5. **Filter Types**: Type-safe criteria builders for both regular models and view models (`StudentDashboardFilter.builder().courseCount().gte(3).build()`).
6. **Metadata Classes**: Static metadata descriptors (`StudentMetadata`, `StudentDashboardMetadata`) to avoid runtime reflection.
7. **Fluent Client API**: Prisma-like database client (`GraphOrmClient`).

## Client API Contract

The generated client provides fluent, type-safe database access with strict compile-time read-only enforcement on views:

```java
GraphOrmClient db = ...;

// Regular Model: Full CRUD
List<Student> students = db.student().findMany(q -> q
    .where(s -> s.departmentId().eq(10))
    .orderBy(s -> s.name().asc())
    .limit(20)
);

Student created = db.student().create(StudentCreateInput.builder()
    .name("Alice")
    .email("alice@example.com")
    .departmentId(10)
    .build()
);

// View Model: Read-Only Access
List<StudentDashboard> dashboards = db.studentDashboard().findMany(q -> q
    .where(d -> d.courseCount().gte(3))
    .orderBy(d -> d.gpa().desc())
    .limit(10)
);

Optional<StudentDashboard> dashboard = db.studentDashboard().findUnique(q -> q
    .where(d -> d.studentId().eq(42))
);

// Read-only invariant: db.studentDashboard().create(...), update(...), delete(...) are NOT generated!
```

## Generation Guidelines
1. **Determinism**: Code generation must produce identical byte-for-byte output for the same input schema. Sort fields, models, views, and imports alphabetically.
2. **Safe Output Directory**: Write generated sources to `target/generated-sources/graphorm/` or a user-configured generated folder. NEVER overwrite handwritten user source code.
3. **Java 21 Modern Syntax**: Use modern Java features: records for immutable models and views, pattern matching, typed builders.
4. **Read-Only Code Segregation**: The generator template for view client accessors must inherit from a `ReadOnlyModelAccessor<T, F>` interface that completely omits write mutation methods.
5. **Compilation Verification**: All generated sources must be verified by compiling them in test suites using `javax.tools.JavaCompiler`.
