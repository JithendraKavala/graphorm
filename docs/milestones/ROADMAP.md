# GraphORM Roadmap

This document tracks all milestones and their completion status. Milestone details are in the individual documents linked below.

---

## Milestone Status

| Milestone | Title | Status | Document |
|-----------|-------|--------|----------|
| M0 | Repository Foundation | 🟡 In Progress | [M0.md](M0.md) |
| M1 | Core Metadata Engine | ⚪ Not Started | [M1.md](M1.md) |
| M2 | Schema Language and Parser | ⚪ Not Started | [M2.md](M2.md) |
| M3 | PostgreSQL JDBC Layer | ⚪ Not Started | [M3.md](M3.md) |
| M4 | Query Engine and CRUD | ⚪ Not Started | [M4.md](M4.md) |
| M5 | Relationship Engine | ⚪ Not Started | [M5.md](M5.md) |
| M6 | GraphQL Integration | ⚪ Not Started | [M6.md](M6.md) |
| M7 | Spring Boot Starter and CLI | ⚪ Not Started | [M7.md](M7.md) |
| M8 | Advanced PostgreSQL Features | ⚪ Not Started | [M8.md](M8.md) |
| M9 | Transactions, Concurrency and Reliability | ⚪ Not Started | [M9.md](M9.md) |
| M10 | Nexus Commerce Validation | ⚪ Not Started | [M10.md](M10.md) |
| M11 | Stabilization and Release | ⚪ Not Started | [M11.md](M11.md) |

**Status legend:**
- ⚪ Not Started
- 🟡 In Progress
- 🟢 Done (all acceptance criteria satisfied)
- 🔴 Blocked

---

## Dependency Order

Milestones must be completed in dependency order. Do not begin a milestone until all its dependencies are marked 🟢 Done.

```
M0 (Foundation)
  └─ M1 (Core Metadata)
       ├─ M2 (Schema Parser)
       └─ M3 (JDBC Layer)
            └─ M4 (Query Engine & CRUD)
                 ├─ M5 (Relationship Engine)
                 └─ M6 (GraphQL Integration) ← also depends on M5
                      └─ M7 (Spring Boot Starter & CLI)
                           ├─ M8 (Advanced PostgreSQL)
                           ├─ M9 (Transactions & Concurrency)
                           └─ M10 (Nexus Commerce)
                                └─ M11 (Stabilization & Release)
```

---

## Update Policy

- Update milestone status only when all acceptance criteria for that milestone have been verified
- "In Progress" means active development is underway on this branch or PR
- "Done" requires: CI green, all tests passing, documentation updated, PRs merged
- Do not mark a milestone Done to clear the board — this misleads future contributors

---

## Note on Existing Implementation

As of the M0 branch (`feat/m0-repository-foundation`), the repository already contains substantial implementation work in `graphorm-core`, `graphorm-schema`, `graphorm-jdbc`, `graphorm-graphql`, `graphorm-generator`, `graphorm-spring-boot-starter`, and `graphorm-cli`. This work was produced before the milestone structure and engineering standards documented here were established.

The milestone acceptance criteria will be formally verified against this existing code as each milestone is reviewed. Milestones will not be marked Done until the acceptance criteria have been explicitly checked — not assumed.
