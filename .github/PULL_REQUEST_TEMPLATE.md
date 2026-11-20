## Pull Request

### Title

<!-- A concise, imperative title: "feat: add relationship batching for one-to-many" -->

---

### Related Issue / Milestone

<!-- Link to the related GitHub issue or milestone. -->
Closes #

Milestone: <!-- M0, M1, M2, ... -->

---

### Problem Being Solved

<!-- Why is this change needed? What problem or gap does it address? -->

---

### Implementation Description

<!-- Describe what was implemented and how. Focus on design decisions, not on restating what the diff shows. -->

---

### Design Decisions

<!-- Did you make any non-obvious choices? Why did you pick this approach over alternatives? -->

---

### Affected Modules

- [ ] graphorm-core
- [ ] graphorm-schema
- [ ] graphorm-jdbc
- [ ] graphorm-query-engine
- [ ] graphorm-graphql
- [ ] graphorm-generator
- [ ] graphorm-spring-boot-starter
- [ ] graphorm-cli
- [ ] graphorm-testkit
- [ ] graphorm-benchmarks
- [ ] examples/nexus-commerce

---

### Tests Added

<!-- List the tests added. Describe what scenario each test covers. -->
- 
- 

### Verification Performed

<!-- What did you run to verify correctness? -->
- [ ] `mvn clean verify` passes
- [ ] All new tests pass
- [ ] No existing tests were weakened or deleted
- [ ] Integration tests pass against PostgreSQL (if applicable)

---

### Breaking Changes

- [ ] Yes — describe below
- [ ] No

<!-- If yes, what breaks and what is the migration path? -->

---

### Documentation Updated

- [ ] Yes
- [ ] No — not required for this change

<!-- If yes, list what was updated. -->

---

### Known Limitations

<!-- Are there any known edge cases, temporary limitations, or follow-up issues? List them and link GitHub issues if they exist. -->

---

### PR Review Checklist (for reviewer)

- [ ] Correctness — implementation matches the stated intent
- [ ] Architecture — module boundaries are respected; no new circular dependencies
- [ ] API design — public APIs are minimal, well-named, and documented
- [ ] Null handling — null cases are handled explicitly
- [ ] Error handling — meaningful exceptions with useful messages
- [ ] SQL safety — parameterized queries; no string concatenation of user input
- [ ] Performance — no N+1 queries; no unnecessarily expensive operations
- [ ] Regression coverage — bug fixes include a failing-then-passing test
- [ ] Documentation — updated where behavior or APIs changed
- [ ] Backward compatibility — breaking changes are intentional and documented
