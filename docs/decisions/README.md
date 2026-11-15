# Architecture Decision Records

This directory contains the Architecture Decision Records (ADRs) for GraphORM.

An ADR documents a significant architectural decision — why it was made, what alternatives were considered, and what consequences it has.

---

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](ADR-0001-maven-multi-module-architecture.md) | Maven Multi-Module Architecture | Accepted |
| [ADR-0002](ADR-0002-postgresql-only-v1.md) | PostgreSQL-Only in v1 | Accepted |
| [ADR-0003](ADR-0003-jdbc-execution-engine.md) | JDBC-Based Execution Engine | Accepted |
| [ADR-0004](ADR-0004-separate-graphorm-schema-and-graphql-schema.md) | Separate GraphORM Model Schema and GraphQL API Schema | Accepted |
| [ADR-0005](ADR-0005-shared-query-engine.md) | Shared Query Engine for GraphQL and Java Client | Accepted |
| [ADR-0006](ADR-0006-dataloader-relationship-batching.md) | DataLoader-Based Relationship Batching | Accepted |

---

## Status Values

| Status | Meaning |
|--------|---------|
| **Proposed** | Under discussion, not yet decided |
| **Accepted** | Decision made and in force |
| **Deprecated** | Was accepted, now superseded or obsolete |
| **Superseded by** | Replaced by another ADR |

---

## ADR Template

When creating a new ADR:

1. Create a file named `ADR-NNNN-short-title.md` (use the next sequential number)
2. Use the template below

```markdown
# ADR-NNNN: Title

**Date:** YYYY-MM-DD
**Status:** Proposed | Accepted | Deprecated | Superseded by ADR-XXXX
**Deciders:** [Names or roles]

---

## Context

[What situation or problem prompted this decision?]

## Decision

[What was decided? Be precise.]

## Alternatives Considered

| Alternative | Why Rejected |
|-------------|-------------|
| | |

## Consequences

**Positive:**
- 

**Negative / Trade-offs:**
- 

## Compliance

[How do we know this decision is being followed? What enforces it?]
```

---

## How to Propose an ADR

1. Create a draft ADR with status **Proposed**
2. Open a GitHub issue using the [Architecture Proposal template](../../.github/ISSUE_TEMPLATE/architecture_proposal.md)
3. Discuss in the issue
4. Update the ADR status to **Accepted** when consensus is reached
5. Add it to the index above

Never silently reverse an accepted ADR. Create a new ADR that supersedes it.
