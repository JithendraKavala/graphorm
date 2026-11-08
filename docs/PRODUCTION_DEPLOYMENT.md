# GraphORM: Production Deployment & GraalVM Native-Image Guide

This guide details best practices for deploying GraphORM applications to high-throughput production environments, including **GraalVM Native Image (AOT)** compilation, **Docker/Kubernetes** containerization, **HikariCP** connection pool tuning, **Micrometer observability**, **security hardening**, and **Spring Boot 3.2 / 3.3 / 4.0 compatibility**.

---

## 1. GraalVM Native-Image (AOT) Compilation

GraphORM is fully GraalVM Native-Image ready out of the box. Using Spring Boot 3.2+ AOT and `graphorm-spring-boot-starter`, GraphORM automatically registers all necessary reflection, resource, proxy, and serialization reachability hints via `GraphOrmRuntimeHintsRegistrar`.

### 1.1 Benefits in Production
- **Sub-50ms Cold Starts**: Instant application bootstrap ideal for serverless, autoscaling worker pools, and fast restarts.
- **Minimal Memory Footprint**: Typically under 60MB RSS at steady state compared to 300MB+ on standard JVMs.
- **Smaller Container Images**: Distroless or scratch container images down to ~40MB total image size.

### 1.2 Maven Native Profile Configuration

Add the GraalVM Native Build Tools plugin to your application `pom.xml`:

```xml
<profiles>
    <profile>
        <id>native</id>
        <build>
            <plugins>
                <plugin>
                    <groupId>org.graalvm.buildtools</groupId>
                    <artifactId>native-maven-plugin</artifactId>
                    <version>0.10.1</version>
                    <extensions>true</extensions>
                    <executions>
                        <execution>
                            <id>build-native</id>
                            <goals>
                                <goal>compile-no-fork</goal>
                            </goals>
                            <phase>package</phase>
                        </execution>
                    </executions>
                    <configuration>
                        <buildArgs>
                            <buildArg>--initialize-at-build-time=org.antlr.v4.runtime</buildArg>
                            <buildArg>-H:+ReportExceptionStackTraces</buildArg>
                        </buildArgs>
                    </configuration>
                </plugin>
            </plugins>
        </build>
    </profile>
</profiles>
```

### 1.3 Building the Native Executable

Ensure GraalVM for JDK 21+ is installed (`native-image --version`), then run:

```bash
mvn clean package -Pnative -DskipTests
```

The resulting standalone native executable is created at `target/<artifact-name>`.

---

## 2. Production Docker & Containerization

### 2.1 Multi-Stage Distroless Native Image Dockerfile

```dockerfile
# Stage 1: Build native executable
FROM ghcr.io/graalvm/native-image-community:21-ol9 AS build
WORKDIR /app

# Install Maven
RUN microdnf install -y maven

COPY pom.xml .
COPY src ./src

# Generate GraphORM sources and compile native binary
RUN mvn clean package -Pnative -DskipTests

# Stage 2: Distroless minimal runtime
FROM gcr.io/distroless/cc-debian12:nonroot
WORKDIR /app

COPY --from=build /app/target/university-app /app/application

USER nonroot:nonroot
EXPOSE 8080

ENTRYPOINT ["/app/application"]
```

### 2.2 Standard JVM Containerization (Java 21)

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Security: Run as non-privileged user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY target/*.jar app.jar

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -XX:MaxRAMPercentage=75.0"
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
```

---

## 3. High-Throughput Connection Pool Tuning (HikariCP)

For PostgreSQL production workloads, configure HikariCP in `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:university_db}?sslmode=require
    username: ${DB_USER:app}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      pool-name: GraphOrmHikariPool
      maximum-pool-size: 30
      minimum-idle: 10
      idle-timeout: 600000        # 10 minutes
      max-lifetime: 1800000        # 30 minutes
      connection-timeout: 20000    # 20 seconds
      leak-detection-threshold: 2000 # Log warning if connection held > 2s
      validation-timeout: 3000

graphorm:
  batch-size: 100
  sql-logging: false               # Disable verbose query logging in production
```

> **Sizing Rule of Thumb**:
> `maximum-pool-size = (core_count * 2) + effective_spindle_count`. For modern SSD / NVMe cloud instances, a pool of 20 to 50 connections per instance is typically optimal.

---

## 4. Production Observability & Monitoring

GraphORM provides native Micrometer instrumentation for metrics, tracing, and APM systems (Prometheus, Datadog, Grafana):

### 4.1 Prometheus Metric Keys

| Metric Key | Type | Tags | Description |
|---|---|---|---|
| `graphorm.query.duration` | Timer | `fingerprint`, `status` | Execution latency of SQL statements (p50, p95, p99) |
| `graphorm.query.count` | Counter | `fingerprint`, `status` | Total SQL executions grouped by query shape |
| `graphorm.dataloader.batch.size` | DistributionSummary | `key` | Number of items loaded per DataLoader batched query |
| `graphorm.dataloader.batch.duration` | Timer | `key` | Latency of batched relationship loading |

### 4.2 Query Fingerprinting & Data Privacy

GraphORM automatically calculates SHA-256 SQL fingerprints:
- Replaces literal numbers, strings, and bound parameters with positional markers (`?`).
- Normalizes variable-length IN-clauses (e.g. `IN (?, ?, ?)` -> `IN (?)`).
- Guarantees zero sensitive data or PII is leaked to APM logs or metrics dashboards.

### 4.3 Query Plan Explainability Hook

To inspect PostgreSQL query execution plans in staging or debug environments:

```java
@Autowired
private JdbcExecutor executor;

public void logPlan(SelectQuery query) {
    String planJson = executor.explainPlan(query);
    log.info("PostgreSQL EXPLAIN plan: {}", planJson);
}
```

---

## 5. Security & DoS Attack Hardening

1. **GraphQL Depth Limits**: Configure `graphorm.security.max-query-depth=10` to immediately reject maliciously nested GraphQL queries.
2. **GraphQL Complexity Limits**: Configure `graphorm.security.max-query-complexity=200`. GraphORM multiplies relation costs proportionally by requested connection batch sizes to prevent resource exhaustion.
3. **Field & Entity Authorization**: Use `@auth(roles: ["ADMIN"])` and `@@auth` directives. When unauthorized callers request protected fields, GraphORM masks internal details and returns sanitized `ACCESS_DENIED` errors.
4. **Parameter-Limit Chunking**: GraphORM automatically partitions IN-lists larger than 1,000 items and batch inserts larger than 5,000 parameters to prevent PostgreSQL's 65,535 parameter limit overflow.

---

## 6. Spring Boot 3.2, 3.3 & Spring Boot 4 Readiness

- **Spring Boot 3.2 & 3.3 Compatibility**: Fully verified against Spring Boot 3.2.4 and 3.3+ auto-configuration contracts.
- **Jakarta EE 11 & Java 21+**: GraphORM is compiled natively with Java 21 release target and standard Jakarta namespace (`jakarta.annotation.*`).
- **Spring Boot 4 Readiness Strategy**:
  - Auto-configuration registration uses modern `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
  - GraalVM reachability hints use `META-INF/spring/aot.factories` with zero deprecated Spring APIs.
  - Ready for Spring Framework 7 and Spring Boot 4 upon release.
