# Git Workflow

This document defines the branch strategy, commit conventions, and merge rules for GraphORM.

---

## Branch Strategy

| Branch | Protection | Purpose |
|--------|-----------|---------|
| `main` | Protected — no direct push, no force push | Stable, released state |
| `feat/<short-description>` | — | New features |
| `fix/<short-description>` | — | Bug fixes |
| `hotfix/<short-description>` | — | Critical production fixes |
| `refactor/<short-description>` | — | Refactoring without behavior change |
| `docs/<short-description>` | — | Documentation-only changes |
| `test/<short-description>` | — | Test-only additions or fixes |
| `chore/<short-description>` | — | Build, CI, or maintenance changes |

**Rules:**
- Never commit directly to `main`
- One branch per logical issue or feature
- Keep branches short-lived — open a PR when the work unit is complete
- Delete branches after merging

---

## Commit Message Convention

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>[optional scope]: <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | When to use |
|------|------------|
| `feat` | A new feature |
| `fix` | A bug fix |
| `test` | Adding or modifying tests (no production code change) |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `docs` | Documentation only |
| `chore` | Build system, CI, dependency updates, maintenance |
| `perf` | A performance improvement |
| `revert` | Reverting a previous commit |

### Examples

```
feat: add schema metadata registry
feat(schema): add enum support with @map directive
fix: handle nullable relation keys in batch loader
fix(jdbc): release connection on rollback failure
test: add relationship batching query-count regression test
refactor: separate SQL compilation from JDBC execution
docs: document transaction isolation semantics
chore: configure maven-checkstyle-plugin
perf(query-engine): cache prepared statement metadata
```

### Breaking Changes

Add `!` after the type or include `BREAKING CHANGE:` in the footer:

```
feat!: change ModelRegistry API to require explicit registration

BREAKING CHANGE: ModelRegistry.register() now takes ModelMetadata instead of Class<?>
```

---

## Workflow

### Starting New Work

```bash
git checkout main
git pull origin main
git checkout -b feat/my-feature
```

### During Development

```bash
# Commit frequently with meaningful messages
git add -p  # Prefer staging hunks over entire files
git commit -m "feat(core): add FieldMetadata immutable record"

# Keep up to date with main (prefer rebase over merge)
git fetch origin
git rebase origin/main
```

### Before Opening a PR

```bash
# Ensure everything builds and tests pass
mvn clean verify

# Review your own diff
git diff origin/main...HEAD

# Check for unintended changes
git log origin/main..HEAD --oneline
```

### Opening a PR

1. Push your branch: `git push -u origin feat/my-feature`
2. Open a PR on GitHub — fill in the [PR template](.github/PULL_REQUEST_TEMPLATE.md) completely
3. Link the related issue
4. Request review from at least one maintainer

### After Approval

- Merge via **Squash and Merge** for feature branches (keeps `main` history linear)
- Merge via **Merge Commit** for release branches (preserves history of release)
- **Rebase and Merge** is acceptable for clean, well-structured branches

### Cleaning Up

```bash
git branch -d feat/my-feature         # Delete local branch
git push origin --delete feat/my-feature  # Delete remote branch
```

---

## Branch Protection Rules

Configure the following on GitHub for `main`:

| Rule | Setting |
|------|---------|
| Require pull request before merging | ✅ Enabled |
| Require approvals | 1 minimum |
| Dismiss stale reviews on push | ✅ Enabled |
| Require status checks to pass | ✅ Enabled — `unit-tests`, `integration-tests`, `verify` |
| Require branches to be up to date | ✅ Enabled |
| Do not allow force pushes | ✅ Enabled |
| Do not allow deletions | ✅ Enabled |

---

## Hotfix Process

For P0 critical bugs:

```bash
git checkout main
git pull origin main
git checkout -b hotfix/sql-injection-in-filter
# Fix, test, and commit
git push -u origin hotfix/sql-injection-in-filter
# Open PR — set title to "hotfix: ..." and mark P0
# After merge, tag the release
git tag v0.1.1
git push origin v0.1.1
```
