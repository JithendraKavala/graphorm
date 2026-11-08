# GraphORM Migration & CLI Guide

This guide covers the **GraphORM Schema Lifecycle and Database Migration Engine** (`graphorm migrate`), distributed concurrency control, and migration strategies when transitioning from **Prisma**, **jOOQ**, or **Hibernate / Spring Data JPA** to GraphORM.

---

## 1. GraphORM Migration Architecture

GraphORM features an integrated, production-grade schema migration engine tailored for PostgreSQL:
- **Declarative Diffing**: Automatically compares the schema AST (`schema.graphorm`) against the live PostgreSQL database catalog (`information_schema` and `pg_catalog`).
- **Dependency Ordering**: Tables, enum types, foreign key constraints, indexes, and views are created and modified in strict topological order to avoid relational constraint violations.
- **Distributed Concurrency Control**: Uses PostgreSQL transaction-level advisory locks (`pg_advisory_xact_lock`) to guarantee that multi-pod applications or concurrent CI/CD pipelines do not execute conflicting migrations.
- **Cryptographic Audit Ledger**: Tracks all applied migrations in `_graphorm_migrations` with SHA-256 checksums, execution timestamps, execution durations, and rollback state.
- **Destructive Change Safeguards**: Detects destructive operations (e.g., dropping columns, altering primary keys, shortening varchar lengths) and requires an explicit `--force` flag before applying.

---

## 2. CLI Command Reference

The migration CLI is invoked via `graphorm migrate <subcommand>`:

### `graphorm migrate diff`
Inspects differences between `schema.graphorm` and the live PostgreSQL database without making any modifications:

```bash
graphorm migrate diff \
  --schema src/main/resources/schema.graphorm \
  --url postgresql://localhost:5432/university_db \
  --user postgres \
  --password secret
```

### `graphorm migrate create`
Generates a new timestamped SQL migration file based on pending schema differences:

```bash
graphorm migrate create \
  --name add_student_gpa_and_indexes \
  --schema src/main/resources/schema.graphorm
```

Outputs migration artifacts into `migrations/<timestamp>_add_student_gpa_and_indexes/`:
- `migration.sql`: Parameterized DDL statements for applying changes
- `rollback.sql`: Reversible statements (when applicable)
- `metadata.json`: SHA-256 checksum, dependencies, and author metadata

### `graphorm migrate apply`
Executes unapplied migrations in chronological order against the target database:

```bash
graphorm migrate apply \
  --url postgresql://localhost:5432/university_db \
  --user postgres \
  --password secret
```

*Flags:*
- `--dry-run`: Prints SQL statements that would be executed without applying them.
- `--force`: Acknowledges and allows destructive operations (e.g. dropping tables or columns).

### `graphorm migrate status`
Displays the current migration state, listing applied vs. pending migrations:

```bash
graphorm migrate status --url postgresql://localhost:5432/university_db
```

Output:
```
GraphORM Migration Status
-------------------------------------------------------------------------------------
Version                Name                           Status       Applied At (UTC)
-------------------------------------------------------------------------------------
20261101120000_init    init_schema                    APPLIED      2026-11-01 12:00:03
20261105093000_auth    add_security_auth_roles        APPLIED      2026-11-05 09:30:14
20261108154500_view    add_student_dashboard_view     PENDING      -
-------------------------------------------------------------------------------------
Total: 3 migrations (2 applied, 1 pending)
```

### `graphorm migrate verify`
Verifies that all applied migrations on the database match the local migration files and haven't been tampered with or modified:

```bash
graphorm migrate verify --url postgresql://localhost:5432/university_db
```

---

## 3. Migrating from Other Technologies

### 3.1 Migrating from Prisma (Node.js / TypeScript)

Prisma developers will find GraphORM immediately intuitive:

| Prisma Concept | GraphORM Equivalent | Advantage in GraphORM |
|---|---|---|
| `schema.prisma` | `schema.graphorm` | Native Java 21 records & Spring Boot starter |
| `prisma db push` / `prisma migrate` | `graphorm migrate apply` | Transactional advisory locking in PostgreSQL |
| Client (`prisma.user.findMany()`) | `db.user().findMany()` | 100% type-safe Java 21 fluent records & lambdas |
| `include: { relation: true }` | Automatic GraphQL / `RelationPlanner` | Eliminates N+1 with single batched `WHERE IN (?)` |
| Computed fields / SQL views | Schema-Defined View Models (`view`) | Pushdown SQL joins & aggregations with zero Java memory leaks |

**Conversion Steps:**
1. Rename your schema file to `schema.graphorm`.
2. Add `@version` to your models for native optimistic concurrency.
3. Replace Prisma query syntax in TypeScript with Java 21 records and client calls.

---

### 3.2 Migrating from jOOQ

While jOOQ offers type-safe SQL construction, it requires reverse-engineering database catalogs into thousands of generated code classes and requires manually writing SQL join projections.

| jOOQ Pattern | GraphORM Equivalent |
|---|---|
| Heavy DSL code generation (`STUDENTS.NAME.eq(...)`) | Clean Java 21 Records + lambda criteria (`s -> s.name().eq(...)`) |
| Manual SQL joins for views | Declarative `view` with `@@from` and `@@leftJoin` |
| Manual GraphQL DataFetcher resolvers | 100% automated GraphQL schema inspection & DataFetcher registration |
| Manual ResultSet mapping | JIT-compiled `DynamicRowMapper` with zero reflection overhead |

**Conversion Steps:**
1. Run `graphorm introspect` against your PostgreSQL database to generate an initial `schema.graphorm`.
2. Replace manual jOOQ record mappings with generated immutable Java 21 records.
3. Replace manual GraphQL DataFetchers with GraphORM's auto-configured Spring GraphQL integration.

---

### 3.3 Migrating from Hibernate / Spring Data JPA

GraphORM solves the fundamental architectural flaws of traditional JPA:
- **No Dirty Checking / Detached State**: No `LazyInitializationException`, no `Hibernate.initialize()`, no detached entity errors.
- **Zero N+1 Query Traps**: Relations are loaded using deterministic, batch-size-chunked `WHERE id IN (?)` queries or unified SQL joins.
- **Immutability First**: Entities are Java 21 records, eliminating state corruption across threads.
- **Predictable SQL**: SQL generation is 100% explicit and visible in logs via `graphorm.sql-logging=true`.

**Comparison Example:**

*JPA Hibernate (Anti-Pattern: N+1 queries, lazy exceptions outside transaction):*
```java
// Risk: Throws LazyInitializationException if accessed outside @Transactional
// Risk: Generates 1 + 100 queries when accessing department
@Entity
public class Student {
    @ManyToOne(fetch = FetchType.LAZY)
    private Department department;
}
```

*GraphORM (Zero N+1, Immutable Java 21 Record, Safe Everywhere):*
```java
// Safe: Student is an immutable record.
// Relations fetched in exactly 2 SQL queries via DataLoader batching.
List<Student> students = db.student().findMany(q -> q
    .where(s -> s.departmentId().eq(1))
    .limit(50));
```
