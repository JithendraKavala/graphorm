---
name: graphorm-spring-boot-integration
description: >-
  Guides the creation and maintenance of graphorm-spring-boot-starter, covering auto-configuration,
  conditional beans, DataSource integration, Spring for GraphQL integration, and transaction handling.
  Use when developing the starter module or configuring Spring Boot sample applications.
---

# GraphORM Spring Boot Integration

This skill guides the implementation of `graphorm-spring-boot-starter` for seamless Spring Boot 3+ applications.

For implementation roadmap and phase specifications, see:
- [PHASE_BREAKDOWN.md - Phase 13](file:///mnt/jithendra/Dev_Station/graphorm/docs/PHASE_BREAKDOWN.md)
- [PROGRESS_TRACKER.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/PROGRESS_TRACKER.md)
- [DEFINITION_OF_DONE.md](file:///mnt/jithendra/Dev_Station/graphorm/docs/DEFINITION_OF_DONE.md)

---

## Auto-Configuration Structure

- Package: `com.graphorm.spring.boot.autoconfigure`
- Auto-configuration class: `GraphOrmAutoConfiguration`
- Registered in: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```java
@AutoConfiguration(after = {DataSourceAutoConfiguration.class, GraphQlAutoConfiguration.class})
@ConditionalOnClass({GraphQL.class, DataSource.class})
@EnableConfigurationProperties(GraphOrmProperties.class)
public class GraphOrmAutoConfiguration { ... }
```

## Configuration Properties

Namespace: `graphorm.*`

```yaml
graphorm:
  schema-path: classpath:schema.graphorm
  dialect: postgresql
  graphql:
    enabled: true
    auto-fetchers: true
    batch-size: 100
  logging:
    show-sql: false
```

## Component Wiring

1. **Schema Registry Bean**: Parses `schema.graphorm` at startup, builds `ModelRegistry`.
2. **JdbcExecutor Bean**: Wraps Spring's managed `DataSource` using `DataSourceUtils` to participate seamlessly in `@Transactional`.
3. **GraphQlSourceCustomizer**: Injects GraphORM DataFetchers and DataLoaders into the `GraphQlSource.Builder` before GraphQL engine initialization.
4. **GraphOrmClient Bean**: Exposes the generated or generic database client for injection into Spring services.

## Startup Diagnostics
- Validate that all models in `schema.graphorm` have corresponding tables/keys.
- Validate that all mapped GraphQL queries match model attributes.
- Print clean diagnostic banners on startup errors.
