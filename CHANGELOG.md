# Changelog

All notable changes to GraphORM will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased]

### Added
- M0: Repository foundation — multi-module Maven structure, engineering documentation, roadmap, CI/CD, PR templates, issue templates, ADRs
- Initial module skeletons: `graphorm-core`, `graphorm-schema`, `graphorm-jdbc`, `graphorm-generator`, `graphorm-graphql`, `graphorm-spring-boot-starter`, `graphorm-cli`, `graphorm-maven-plugin`, `graphorm-query-engine`, `graphorm-testkit`, `graphorm-benchmarks`
- Core exception hierarchy
- Core metadata interfaces (EntityMetadata, FieldMetadata, RelationshipMetadata)
- Schema parser (ANTLR4 grammar, AST nodes, semantic validator)
- SQL query builders (SELECT, INSERT, UPDATE, DELETE, UPSERT)
- JDBC execution engine with HikariCP connection pooling
- GraphQL DataFetcher registration and selection-set processing
- Spring Boot auto-configuration starter
- GitHub Actions CI pipeline

---

## [0.1.0-SNAPSHOT] — Initial Development

This is the initial development snapshot. No stable release has been made yet.

### Notes
- All APIs are subject to change
- PostgreSQL is the only supported database
- Do not use in production
