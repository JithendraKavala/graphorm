# Engineering Rules

These rules apply to every contribution to GraphORM. They are not suggestions. They define the standard of work expected from every change, regardless of size.

---

## 1. Architecture Rules

**1.1** Respect module boundaries. Each module has a defined responsibility. Do not add code to a module because it is convenient — add it to the correct module.

**1.2** Never introduce circular module dependencies. The dependency direction is:

```
graphorm-core
    ↑
graphorm-schema          graphorm-jdbc
    ↑                        ↑
graphorm-generator   graphorm-query-engine
                             ↑
                      graphorm-graphql
                             ↑
                  graphorm-spring-boot-starter
```

`graphorm-core` has no internal dependencies. Every other module may depend on `graphorm-core`. No module above may depend on a module below it in this graph.

**1.3** Keep core abstractions (`graphorm-core`) independent of Spring Boot, GraphQL Java, JDBC implementation classes, and PostgreSQL-specific code.

**1.4** Avoid unnecessary abstraction. Do not create interfaces, factories, or registries for components that have only one implementation and no realistic prospect of needing another.

**1.5** Do not introduce a feature solely for hypothetical future requirements. Every feature must solve a real, current problem.

**1.6** Prefer explicit, understandable designs over clever, compact ones. Code is read more than it is written.

**1.7** Keep PostgreSQL-specific behavior in `graphorm-jdbc`. Do not leak PostgreSQL SQL syntax or PostgreSQL-specific types into `graphorm-core` or `graphorm-graphql`.

**1.8** Do not leak JDBC implementation details into the GraphQL integration layer. The GraphQL layer must work through the query engine abstraction, not by calling JDBC directly.

**1.9** Keep query planning separate from SQL execution. Query plan construction belongs in `graphorm-query-engine`. SQL compilation and JDBC execution belong in `graphorm-jdbc`.

**1.10** Never duplicate query execution logic across the Java client and GraphQL interfaces. Both must share the same underlying query engine.

---

## 2. Java Rules

**2.1** Use Java 21 language features where appropriate. Do not use deprecated patterns that Java 21 supersedes.

**2.2** Follow consistent Java naming conventions:
- Classes: `PascalCase`
- Methods and fields: `camelCase`
- Constants: `UPPER_SNAKE_CASE`
- Packages: `lowercase.with.dots`

**2.3** Prefer immutable objects where practical. Use `final` fields. Do not expose mutable state unnecessarily.

**2.4** Use Java records for suitable immutable data carriers — metadata objects, query parameters, result types, configuration values.

**2.5** Avoid unnecessarily large classes. If a class exceeds ~400 lines, consider splitting it.

**2.6** Avoid giant utility classes. Split utilities by concern into focused classes.

**2.7** Use meaningful exception types from the exception hierarchy in `graphorm-core`. Do not throw raw `RuntimeException` or `Exception`.

**2.8** Never swallow exceptions. If you catch an exception and cannot handle it, re-throw it (possibly wrapped) or log it at an appropriate level before re-throwing.

**2.9** Avoid returning `null` where an explicit result type or `Optional<T>` is semantically appropriate. Use `Optional<T>` for fields that may legitimately be absent. Use empty collections rather than `null` for collections.

**2.10** Document non-obvious public APIs with Javadoc. The goal is to explain **why**, not **what**.

**2.11** Avoid premature use of reflection. If runtime reflection is necessary, document exactly why and test it explicitly.

**2.12** Avoid unnecessary dependencies. Every new dependency must have a clear justification. Do not add a library to solve a problem that can be solved trivially in a few lines.

---

## 3. Database and SQL Rules

**3.1** Always use parameterized SQL (`PreparedStatement` with `?` placeholders). Never format SQL with string concatenation.

**3.2** Never concatenate user-controlled or externally sourced input into a SQL string. This includes GraphQL arguments, filter values, sort values, and pagination cursors.

**3.3** Validate dynamic identifiers (column names, table names) against trusted schema metadata before including them in SQL. Reject any identifier that does not exist in the registry.

**3.4** Handle SQL `NULL` semantics explicitly. `NULL` comparisons in SQL require `IS NULL` / `IS NOT NULL`, not `= NULL`. Document where `NULL` has special meaning.

**3.5** Avoid `SELECT *`. When projection is available, generate explicit column lists. This is both a performance and a correctness requirement.

**3.6** Avoid N+1 queries. Relationship loading must batch queries. Query count tests are required for all relationship-loading paths.

**3.7** Test transaction behavior — commit, rollback, and isolation — with real PostgreSQL, not mocks.

**3.8** Do not hide expensive database operations behind apparently cheap APIs. If an operation may issue multiple queries, document this.

**3.9** Preserve deterministic pagination. Cursor-based and offset-based pagination must produce consistent results when an `ORDER BY` clause is present. Document behavior when no order is specified.

**3.10** Test PostgreSQL-specific functionality against PostgreSQL, not an incompatible substitute. H2, SQLite, and in-memory databases are not acceptable substitutes for PostgreSQL integration tests.

---

## 4. Testing Rules

**4.1** Every bug fix must include a regression test, unless it is genuinely impossible to reproduce the bug in a test.

**4.2** Every new feature must include tests that verify correct behavior and common failure modes.

**4.3** Unit-test isolated logic. Do not start a container to test a pure logic class.

**4.4** Use Testcontainers for PostgreSQL integration tests. Do not use H2, SQLite, or any database that does not support the PostgreSQL-specific features under test.

**4.5** Test failure paths, not only successful execution. Invalid inputs, constraint violations, connection failures, and rollbacks must all be tested.

**4.6** Test null, empty, boundary, and invalid inputs. Do not assume inputs will always be well-formed.

**4.7** Test query counts for relationship loading. The expected number of database queries must be asserted, not assumed.

**4.8** Never disable or weaken a failing test simply to make CI green. If a test fails, fix the code or remove the feature it covers. Document any exception explicitly.

**4.9** Do not use mocks as a substitute for integration testing database behavior. Mocks are appropriate for unit-testing logic that calls a database, not for testing the SQL itself.

**4.10** Keep tests deterministic and independent. Tests must not share database state. Use `@BeforeEach` / `@AfterEach` to reset state, or use separate schemas per test.

---

## 5. Security Rules

**5.1** Never expose internal database columns through the GraphQL API without an explicit field mapping. The schema is opt-in, not opt-out.

**5.2** Do not expose sensitive fields (passwords, tokens, secrets) through generated inputs by default. Mark fields as `@sensitive` in the GraphORM schema to prevent automatic exposure.

**5.3** Validate field and relation access. If a field is not in the metadata registry, reject the request.

**5.4** Use parameterized SQL. (Restatement from Section 3 — it matters enough to appear twice.)

**5.5** Prevent SQL injection through dynamic ordering and identifiers. All dynamic identifiers must be validated against the schema metadata before use.

**5.6** Avoid leaking database credentials or sensitive values in log output. Log query shapes, not query parameters.

**5.7** Treat GraphQL query depth and complexity as resource-consumption concerns. Provide configurable limits. Do not allow unbounded recursive queries.

**5.8** Do not assume that a valid GraphQL field is automatically authorized. Authorization is the responsibility of the consuming application. GraphORM must provide extension points for field-level authorization but must not enforce a specific authorization model.

---

## 6. Code Quality Rules

**6.1** No `TODO` without an associated GitHub issue number or a documented follow-up task. `// TODO: fix this` is not acceptable. `// TODO(#42): handle cursor overflow` is acceptable.

**6.2** No commented-out implementation code committed. Remove it, or put it behind a feature flag with a clear explanation.

**6.3** No unused dependencies in any module POM.

**6.4** No duplicate implementations of the same behavior across modules. If the same logic is needed in two modules, extract it to `graphorm-core` or a shared utility.

**6.5** No unnecessary public APIs. Keep the public surface as small as possible. Use package-private where public access is not required.

**6.6** No arbitrary changes to existing architecture without an ADR and discussion.

**6.7** No suppression of compiler warnings (`@SuppressWarnings`) without a code comment explaining exactly why the suppression is justified.

**6.8** No hardcoded credentials, local machine paths, or developer-specific configuration in committed code.

**6.9** No unreviewed generated files committed to the repository without explicit documentation of the generation process.

**6.10** Keep documentation updated when behavior or public APIs change. A PR that changes a public API without updating documentation is incomplete.

---

## Enforcement

These rules are enforced through:
- Pull request review (all PRs require at least one approval)
- CI checks (compilation, tests, dependency analysis)
- Code review checklist in the PR template

Any deliberate exception to these rules must be documented in the PR description with an explicit justification.
