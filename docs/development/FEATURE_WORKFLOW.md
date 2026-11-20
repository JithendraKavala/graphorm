# Feature Development Workflow

This document defines the end-to-end process for developing any feature in GraphORM. Every feature, regardless of size, follows this process.

---

## Why This Process Exists

GraphORM is a production-quality framework intended to be consumed by other applications. A feature that is half-implemented, untested, or architecturally inconsistent will compound costs in every downstream consumer. The process below ensures features are complete, correct, and maintainable before they are merged.

---

## The Process

### Step 1 — Identify the Requirement

- What problem does this feature solve?
- Who benefits from it?
- Is it in-scope for the current milestone?
- Has someone else already implemented something similar?

If the requirement is vague, clarify it before proceeding.

---

### Step 2 — Check Existing Architecture and Implementation

Before writing a line of code:

1. Read the relevant module's `README.md` and `docs/` documentation
2. Search the codebase for related interfaces, classes, and patterns
3. Understand how the new feature will interact with existing code
4. Identify which modules will be affected

Do not implement a feature without understanding what already exists.

---

### Step 3 — Create or Identify a GitHub Issue

Every feature must have a GitHub issue:

- Use the [Feature Request template](.github/ISSUE_TEMPLATE/feature_request.md)
- Assign a milestone
- Add appropriate labels (`type:feature`, `module:*`, `priority:*`)
- Link to related issues or ADRs

If the feature is substantial, create sub-issues for each significant piece of work.

---

### Step 4 — Define Expected Behavior

Write down:

- What the feature does, in plain language
- What it does NOT do (explicit out-of-scope)
- Failure modes and how they are handled
- Edge cases (null inputs, empty collections, missing relations, etc.)
- API shape (method signatures, schema syntax, configuration properties)

Do not start implementation without this clarity.

---

### Step 5 — Identify Affected Modules

List every module that must change. Verify the changes do not introduce circular dependencies. If new module-to-module dependencies are required, document them.

---

### Step 6 — Document Any Architectural Decision

If the feature:
- Changes a module boundary
- Introduces a new external dependency
- Changes a public API
- Reverses or modifies an existing ADR
- Requires a significant design choice between alternatives

Then write an ADR in `docs/decisions/` before implementation begins. See `docs/decisions/README.md` for the template.

---

### Step 7 — Write Acceptance Criteria

Acceptance criteria must be:
- Specific and measurable
- Independently verifiable
- Complete (a "done" feature satisfies all of them)

Example for a filter feature:

```
- [ ] findMany with { where: { name: { startsWith: "A" } } } returns only matching records
- [ ] Filter compiles to a parameterized WHERE clause
- [ ] SQL is verified not to contain concatenated string literals
- [ ] Null filter value returns all records (or is explicitly rejected — document which)
- [ ] Integration test passes against PostgreSQL
- [ ] Query count is verified to be 1
```

---

### Step 8 — Design Tests

Before writing production code, design the tests:

- What unit tests are needed?
- What integration tests are needed?
- Are there query-count tests?
- Are there boundary or failure tests?

Write test method stubs if helpful. This clarifies what "done" means.

---

### Step 9 — Create a Feature Branch

```bash
git checkout main
git pull origin main
git checkout -b feat/filter-startsWith
```

---

### Step 10 — Implement Incrementally

- Implement in small, coherent commits
- Keep the build passing after each meaningful commit
- Do not accumulate large uncommitted changes
- Run `mvn clean test` frequently during development

Commit examples:

```
feat(query-engine): add StringFilter record with startsWith field
feat(jdbc): compile StringFilter.startsWith to ILIKE clause
test(jdbc): add integration tests for StringFilter
docs: document StringFilter behavior with null values
```

---

### Step 11 — Run Relevant Tests

Before opening a PR:

```bash
# Full build and test
mvn clean verify

# Or targeted at the affected modules
mvn clean verify -pl graphorm-jdbc,graphorm-query-engine
```

Confirm:
- All new tests pass
- All existing tests still pass
- No tests have been weakened or skipped

---

### Step 12 — Review the Diff

Before opening a PR, review your own changes:

```bash
git diff origin/main...HEAD
```

Ask yourself:
- Is every line of this diff intentional?
- Are there debug statements, TODOs without issues, commented code?
- Is the change appropriately scoped, or does it contain unrelated modifications?
- Are there missing test cases?
- Is the documentation updated?

---

### Step 13 — Open a Pull Request

Fill in the [PR template](.github/PULL_REQUEST_TEMPLATE.md) completely.

A PR that skips required sections will be sent back for revision before review begins.

For large features, prefer multiple smaller PRs:

| PR | Contains |
|----|---------|
| PR 1 | Interfaces and records (no implementation) |
| PR 2 | Implementation |
| PR 3 | Integration tests |
| PR 4 | Documentation updates |

Each PR must be independently buildable and not break `mvn clean verify`.

---

### Step 14 — Address Review Comments

- Respond to every comment, even if only to explain disagreement
- Do not resolve comments silently by pushing a fix without explanation
- If a comment leads to a significant change, re-request review after the change

---

### Step 15 — Merge Only After Approval and Passing CI

Before merging, confirm:
- [ ] At least one approval from a maintainer
- [ ] All CI checks are green (unit tests, integration tests, full verify)
- [ ] All review comments are resolved
- [ ] The branch is up to date with `main`

---

### Step 16 — Update the Roadmap and Close the Issue

After merging:

1. Close the GitHub issue (usually done automatically via `Closes #N` in the PR)
2. Update milestone progress in `docs/milestones/ROADMAP.md`
3. Update the relevant milestone document if acceptance criteria are now satisfied

---

## Definition of Done

A feature is complete when **all** of the following are true:

| Criterion | Verified by |
|-----------|------------|
| Implementation is complete | Code review |
| All acceptance criteria are satisfied | Reviewer checklist |
| Tests are included and passing | CI |
| No existing tests have been weakened | Reviewer + CI |
| All modules compile | CI |
| Code follows engineering rules | Code review |
| No accidental unrelated changes | Reviewer diff check |
| Documentation is updated where necessary | Reviewer |
| PR has been reviewed and approved | GitHub PR status |
| CI is green | GitHub CI status |
| Change is merged | GitHub merge status |

A feature that is "mostly done" is not done.
