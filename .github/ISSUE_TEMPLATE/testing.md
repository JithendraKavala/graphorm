---
name: "\U0001F9EA Testing Improvement"
about: "Report missing test coverage or propose a new test"
title: "test: "
labels: ["type:testing", "priority:medium"]
assignees: ""
---

## Testing Issue

## What Is Untested or Inadequately Tested?

<!-- Describe the behavior, edge case, or scenario that lacks sufficient test coverage. -->

## Why Is This Important?

<!-- What bug could this missing test allow? What regression is at risk? -->

## Suggested Test Approach

<!-- Unit test, integration test with Testcontainers, N+1 query count test, etc. -->
- [ ] Unit test
- [ ] Integration test (Testcontainers + PostgreSQL)
- [ ] Query count / N+1 regression test
- [ ] Performance / JMH benchmark
- [ ] Contract / API test

## Affected Module

- [ ] graphorm-core
- [ ] graphorm-schema
- [ ] graphorm-jdbc
- [ ] graphorm-query-engine
- [ ] graphorm-graphql
- [ ] graphorm-generator
- [ ] graphorm-spring-boot-starter
- [ ] graphorm-testkit

## Additional Context

<!-- Any related issues or PRs. -->
