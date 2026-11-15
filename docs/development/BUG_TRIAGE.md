# Bug Triage Policy

Every bug reported in GraphORM follows this process. The goal is to fix the actual root cause — not to patch visible symptoms — and to prevent the bug from recurring.

---

## Step 1 — Reproduce

Before modifying any code:

1. Read the bug report carefully and understand the reported behavior.
2. Identify the affected module (core, schema, jdbc, query-engine, graphql, generator, spring-boot-starter, cli).
3. Write the smallest possible failing test or schema that reproduces the problem.
4. Record the **expected behavior** (what should happen).
5. Record the **actual behavior** (what does happen — include stack traces, SQL output, error messages).
6. Determine whether the issue is a **regression** (worked in a previous commit) or was never correct.

**Do not change code based only on reading an error message.** Understand the cause first.

---

## Step 2 — Classify Severity

### P0 — Critical

Stop unrelated feature work. Fix immediately.

**Examples:**
- Data corruption (incorrect records written to the database)
- SQL injection vulnerability
- Transactions committing incorrect data
- Severe security vulnerabilities
- Complete framework failure (e.g., nothing initializes)

**Action:**
- Create an urgent `hotfix/<short-description>` branch
- Fix the root cause
- Add regression tests
- Require review before merging
- Do not merge without CI passing

---

### P1 — High

Prioritize in the current milestone. Fix before adding dependent features.

**Examples:**
- Core CRUD operations (findMany, create, update, delete) produce incorrect results
- Relationship resolution returns incorrect records
- Common GraphQL queries fail
- Connection leaks
- Serious transaction bugs (rollback not working)

**Action:**
- Create a `fix/<short-description>` branch in the current sprint
- Fix before introducing features that build on the broken behavior

---

### P2 — Medium

Add to the current or next milestone. Fix before declaring the affected feature stable.

**Examples:**
- Incorrect behavior in less common filters (e.g., nested OR with NULL)
- Edge cases in nested pagination
- Incorrect but non-destructive error handling (wrong exception type, misleading message)
- Performance regressions in particular query patterns

**Action:**
- Add to the current milestone backlog or the next milestone
- Must be fixed before the affected feature milestone is declared done

---

### P3 — Low

Add to backlog. Fix when appropriate.

**Examples:**
- Documentation mistakes
- Minor or misleading diagnostic messages
- Non-critical developer experience issues (e.g., confusing log output)

**Action:**
- Add a `docs:` or `fix:` issue to the backlog
- Fix when it makes sense in context

---

## Step 3 — Determine the Root Cause

Investigate systematically. The fix should target the actual source of the bug, not its symptom.

Ask:

| Layer | Question |
|-------|---------|
| Schema parser | Is the schema being parsed incorrectly? Is the AST wrong? |
| Metadata | Is the metadata for a model, field, or relationship incorrect? |
| Query planning | Is the query plan incorrect — wrong projection, wrong joins? |
| SQL generation | Is the SQL output incorrect — wrong clauses, wrong parameter binding? |
| JDBC mapping | Is the result incorrectly mapped from `ResultSet` to Java? |
| Relationship batching | Is the batched IN query wrong? Is grouping by parent ID incorrect? |
| GraphQL mapping | Is the GraphQL field mapped to the wrong model field? |
| Spring configuration | Is the auto-configuration wiring something incorrectly? |
| Test environment | Is the test infrastructure itself responsible (e.g., stale schema)? |

---

## Step 4 — Implement a Regression Test

Write a **failing test first** that demonstrates the bug. The test should fail before the fix and pass after it.

Then implement the **smallest appropriate fix**.

Verify:
- [ ] The regression test passes after the fix
- [ ] Related existing tests still pass
- [ ] The full affected module test suite passes (`mvn test -pl graphorm-<module>`)
- [ ] The complete Maven verify succeeds (`mvn clean verify`)

---

## Step 5 — Update Documentation

After fixing the bug, update relevant documentation if the bug exposed:

- An undocumented behavior or API contract
- An incorrect API description
- A missing limitation
- A misleading error message
- An architectural assumption that needs clarification

Do not create documentation for every trivial typo fix. Use judgment.

---

## Regression Policy

Every merged fix must include a regression test that:

1. Demonstrates the broken behavior (before fix: test fails)
2. Confirms the fix is correct (after fix: test passes)
3. Will catch the same regression in the future (the test must remain in CI permanently)

No exceptions unless the bug is physically impossible to reproduce in a test (document this explicitly in the PR).
