# Contributing to GraphORM

Thank you for your interest in contributing to GraphORM! This document explains how to participate productively.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [How to Contribute](#how-to-contribute)
- [Branch Strategy](#branch-strategy)
- [Commit Messages](#commit-messages)
- [Pull Request Guidelines](#pull-request-guidelines)
- [Engineering Standards](#engineering-standards)
- [Testing Requirements](#testing-requirements)
- [Definition of Done](#definition-of-done)

---

## Code of Conduct

All contributors must follow the [Code of Conduct](CODE_OF_CONDUCT.md). Be respectful, constructive, and professional.

---

## Getting Started

### Prerequisites

- Java 21 (Temurin recommended)
- Maven 3.9+
- Docker (for Testcontainers integration tests)
- Git

### Build the project

```bash
git clone https://github.com/JithendraKavala/graphorm.git
cd graphorm
mvn clean verify
```

### Run unit tests only (no Docker required)

```bash
mvn clean test
```

### Run integration tests (requires Docker)

```bash
mvn clean verify -P integration-tests
```

---

## How to Contribute

1. **Find or create an issue.** Check [open issues](https://github.com/JithendraKavala/graphorm/issues) first.
2. **Discuss.** For significant changes, open a discussion or comment on the issue before starting implementation.
3. **Fork or branch.** Use the branch naming conventions below.
4. **Implement.** Follow the engineering standards.
5. **Test.** Every change must include appropriate tests.
6. **Open a PR.** Fill in the PR template completely.
7. **Address review.** Respond to all review comments before merging.

---

## Branch Strategy

| Branch | Purpose |
|--------|---------|
| `main` | Stable, protected. No direct pushes. |
| `feat/<short-description>` | New features |
| `fix/<short-description>` | Bug fixes |
| `hotfix/<short-description>` | Critical production fixes |
| `refactor/<short-description>` | Refactoring without behavior change |
| `docs/<short-description>` | Documentation-only changes |
| `test/<short-description>` | Test-only changes |
| `chore/<short-description>` | Build and maintenance |

---

## Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add schema metadata registry
fix: handle nullable relation keys
test: add relationship batching regression tests
refactor: separate SQL compilation from execution
docs: document transaction semantics
chore: configure Maven compiler plugin
```

- **Present tense**, imperative mood: "add", not "added" or "adds"
- **Scope is optional** but encouraged: `feat(schema): add enum support`
- **Breaking changes**: add `!` after type or `BREAKING CHANGE:` in footer

---

## Pull Request Guidelines

Fill in the [PR template](.github/PULL_REQUEST_TEMPLATE.md) completely.

A PR must:
- Address one logical unit of work
- Include appropriate tests
- Pass all CI checks
- Be reviewed and approved before merging
- Not weaken or disable existing tests

For large features, split into smaller reviewable PRs (e.g., interfaces first, implementation second, tests third).

---

## Engineering Standards

Read the full [Engineering Rules](docs/development/ENGINEERING_RULES.md) before contributing.

Key points:
- Respect module boundaries — do not introduce cross-module circular dependencies
- Always use parameterized SQL — never concatenate user input
- PostgreSQL only in v1 — do not add MySQL, SQLite, or H2 support
- Use Java records for immutable data carriers
- Document non-obvious public APIs

---

## Testing Requirements

- Every new feature must include tests
- Every bug fix must include a regression test
- Use Testcontainers for PostgreSQL integration tests
- Do not mock database behavior in integration tests
- Test null, empty, boundary, and invalid inputs
- Keep tests deterministic and independent

See [Testing Strategy](docs/TESTING_STRATEGY.md) for full guidelines.

---

## Definition of Done

A contribution is complete only when:

- [ ] Implementation is complete and correct
- [ ] Acceptance criteria are satisfied
- [ ] Tests are included and passing
- [ ] No existing tests have been weakened
- [ ] All modules compile (`mvn clean verify`)
- [ ] Code follows engineering rules
- [ ] No unrelated changes are included
- [ ] Documentation is updated where necessary
- [ ] PR has been reviewed and approved
- [ ] CI is green

---

## Questions?

Open a [Discussion](https://github.com/JithendraKavala/graphorm/discussions) or comment on the relevant issue.
