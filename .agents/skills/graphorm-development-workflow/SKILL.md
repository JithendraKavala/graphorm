---
name: graphorm-development-workflow
description: >-
  Guides AI coding agents through the step-by-step workflow for implementing features, running tests,
  managing roadmap progress, and maintaining documentation. Enforces the "Test Everything as Much as Possible" protocol.
  Use at the beginning and end of any task.
---

# GraphORM Development Workflow & Roadmap Protocol

This skill dictates the non-negotiable operational discipline that every AI coding agent must follow when performing work on the GraphORM repository.

## The Mandatory 9-Step Agent Execution Workflow

Every agent assigned a task must execute these 9 steps sequentially:

1. **Read the Implementation Roadmap**: Read `docs/IMPLEMENTATION_ROADMAP.md` to understand current progress and overarching milestone requirements.
2. **Identify the Current Phase**: Check `docs/PROGRESS_TRACKER.md` to verify the active phase and ensure prior dependent phases are 100% completed.
3. **Read the Corresponding Phase Tasks**: Open `docs/PHASE_BREAKDOWN.md`, locate the active phase, and read the exact task specifications, class names, dependencies, and completion criteria.
4. **Inspect Existing Code Before Modifying It**: Verify existing files, package structures, and git status. Check `graphorm-core` abstractions to prevent duplicate models, interfaces, or query ASTs.
5. **Implement Only the Requested Task & Its Dependencies**: Focus strictly on the assigned Task ID. Never implement speculative features from future roadmap phases.
6. **Write Exhaustive Tests**: Follow the "Test Everything as Much as Possible" protocol. Write unit tests, dialect verification tests, compiler tests, and Testcontainers PostgreSQL integration tests.
7. **Verify Acceptance Criteria**: Verify all completion criteria against `docs/PHASE_BREAKDOWN.md` and check compliance with the [Definition of Done](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md).
8. **Update Progress Documentation**: Update `docs/PROGRESS_TRACKER.md` and `docs/IMPLEMENTATION_ROADMAP.md` with completed task status, PR links, and test files.
9. **Never Silently Skip Phases or Introduce Unplanned Architecture**: Do not jump ahead across phases, modify architectural boundaries, or create duplicate query engines without updating documentation and getting explicit developer approval.

---

## Active Development Phase: "Test Everything as Much as Possible"

During implementation:
1. **Test-First / Comprehensive Testing**: Write unit tests alongside every feature, grammar rule, query builder, and dialect method. Test all edge cases, nullability variations, and error conditions.
2. **One Feature at a Time**: Focus strictly on the assigned task or phase.
3. **Strict Parameterization**: Never interpolate raw strings into SQL. Produce `BoundSql` with positional parameter markers (`?`).
4. **Continuous Test Verification**: Run unit tests after each logical step (`mvn test`).
5. **Compile-Check Generated Code**: Ensure all code generator templates produce clean, compilable Java 21 records and clients using `javax.tools.JavaCompiler`.
6. **Real Database Testing**: Use Testcontainers PostgreSQL integration tests for database operations—never mock database execution.
7. **N+1 Verification**: Assert exact query counts using SQL statement trackers for all relationship resolvers and view queries.
8. **Respect Immutability**: Ensure metadata objects remain immutable after construction.

---

## Post-Development Phase & Verification Checklist

Before ending any turn or declaring a task complete:
1. **Run Full Test Suite**: Verify that all unit, compiler, and integration tests pass without regressions (`mvn clean test-compile test`).
2. **Check Definition of Done**: Validate all criteria in `docs/DEFINITION_OF_DONE.md`.
3. **Update Documentation**:
   - Update task status in `docs/PROGRESS_TRACKER.md`.
   - If an architectural decision was made, append it to `docs/ARCHITECTURAL_DECISIONS.md`.
4. **Provide Verification Summary**: Clearly explain what was implemented, list the exact tests written and executed, and document how the next agent can proceed.
