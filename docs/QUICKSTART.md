# GraphORM: 5-Minute Quickstart

Get up and running with GraphORM in under 5 minutes. This tutorial guides you through defining a schema, integrating automatic code generation into Maven or Gradle, bootstrapping a Spring Boot application, executing type-safe queries, deploying automatic GraphQL endpoints, and managing database migrations.

---

## Prerequisites

- **Java**: OpenJDK 21 or later
- **Build Tool**: Maven 3.9+ or Gradle 8.5+
- **Database**: PostgreSQL 15+ (local instance or Docker container)

---

## Step 1: Add Dependencies & Build Integration

### Option A: Maven Integration (`graphorm-maven-plugin`)

Add the GraphORM Spring Boot Starter and the dedicated Maven plugin to your `pom.xml`. The Maven plugin automatically compiles your schema into Java 21 records and client accessors during `mvn compile` / `mvn generate-sources`:

```xml
<dependencies>
    <!-- GraphORM Spring Boot Starter -->
    <dependency>
        <groupId>com.graphorm</groupId>
        <artifactId>graphorm-spring-boot-starter</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring Boot Web & GraphQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-graphql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- GraphORM Code Generation Plugin -->
        <plugin>
            <groupId>com.graphorm</groupId>
            <artifactId>graphorm-maven-plugin</artifactId>
            <version>0.1.0-SNAPSHOT</version>
            <executions>
                <execution>
                    <goals>
                        <goal>generate</goal>
                    </goals>
                    <configuration>
                        <schemaFile>${project.basedir}/src/main/resources/schema.graphorm</schemaFile>
                        <outputDirectory>${project.build.directory}/generated-sources/graphorm</outputDirectory>
                        <basePackage>com.example.university</basePackage>
                        <addSource>true</addSource>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

### Option B: Gradle Integration

For Gradle projects, configure code generation via the GraphORM CLI or direct JavaExec task:

```groovy
configurations {
    graphormCli
}

dependencies {
    graphormCli 'com.graphorm:graphorm-cli:0.1.0-SNAPSHOT'
    implementation 'com.graphorm:graphorm-spring-boot-starter:0.1.0-SNAPSHOT'
}

task generateGraphOrmSources(type: JavaExec) {
    classpath = configurations.graphormCli
    mainClass = 'com.graphorm.cli.GraphOrmCli'
    args = [
        'generate',
        '--schema', "${projectDir}/src/main/resources/schema.graphorm",
        '--output', "${buildDir}/generated-sources/graphorm",
        '--package', 'com.example.university'
    ]
}

compileJava.dependsOn generateGraphOrmSources
sourceSets.main.java.srcDir "${buildDir}/generated-sources/graphorm"
```

---

## Step 2: Define Your Schema (`schema.graphorm`)

Place your data model in `src/main/resources/schema.graphorm`:

```prisma
datasource db {
  provider = "postgresql"
  url      = "postgresql://localhost:5432/university_db"
}

generator client {
  provider = "graphorm-client-java"
  package  = "com.example.university"
}

model Department {
  id       Int       @id @default(autoincrement())
  name     String    @map("dept_name")
  code     String    @unique
  students Student[]

  @@map("departments")
}

model Student {
  id           Int         @id @default(autoincrement())
  name         String
  email        String      @unique
  gpa          Float?
  version      Int         @default(1) @version
  departmentId Int         @map("department_id")
  department   Department? @relation(fields: [departmentId], references: [id])

  @@map("students")
  @@index([departmentId], name: "idx_students_department")
}

// Stage 2 Virtual View Model with Aggregations
view StudentDashboard {
  studentId      Int     @map("s.id")
  studentName    String  @map("s.name")
  departmentName String? @map("d.dept_name")
  gpa            Float?  @map("s.gpa")

  @@from(Student, as: "s")
  @@leftJoin(Department, as: "d", on: "s.departmentId == d.id")
  @@groupBy(["s.id", "s.name", "d.dept_name", "s.gpa"])
}
```

---

## Step 3: Run Database Migrations

Use the `graphorm migrate` CLI to create and apply PostgreSQL migrations:

```bash
# Verify schema syntax and PostgreSQL catalog diff
graphorm migrate diff

# Create and apply migration
graphorm migrate create --name init_schema
graphorm migrate apply

# Check migration ledger status
graphorm migrate status
```

---

## Step 4: Configure `application.yml`

In `src/main/resources/application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/university_db
    username: postgres
    password: secret
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000

graphorm:
  schema-location: classpath:schema.graphorm
  dialect: postgresql
  sql-logging: true
  batch-size: 100
  graphql:
    enabled: true
    auto-fetchers: true
  metrics:
    enabled: true
```

---

## Step 5: Query with Type-Safe Java 21 Client

Inject `GraphOrmClient` into your Spring services:

```java
package com.example.university.service;

import com.example.university.client.GraphOrmClient;
import com.example.university.input.StudentCreateInput;
import com.example.university.model.Student;
import com.example.university.model.StudentDashboard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StudentService {

    private final GraphOrmClient db;

    public StudentService(GraphOrmClient db) {
        this.db = db;
    }

    @Transactional
    public Student createStudent(String name, String email, int departmentId) {
        return db.student().create(StudentCreateInput.builder()
                .name(name)
                .email(email)
                .departmentId(departmentId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Student> findHonorStudents(int deptId) {
        return db.student().findMany(q -> q
                .where(s -> s.departmentId().eq(deptId).and(s.gpa().gte(3.5)))
                .orderBy(s -> s.name().asc())
                .limit(25));
    }

    @Transactional(readOnly = true)
    public List<StudentDashboard> getDashboard() {
        // Read-only view with pushdown SQL joins and GROUP BY
        return db.studentDashboard().findMany(q -> q
                .limit(50));
    }
}
```

---

## Step 6: Automatic GraphQL API

With `spring-boot-starter-graphql` and `graphorm-spring-boot-starter`, queries, mutations, relationship resolvers, and Relay-style cursor pagination are automatically registered:

```graphql
# Query students with nested relationship batch loading (Zero N+1 queries)
query {
  students(limit: 10) {
    id
    name
    email
    department {
      name
      code
    }
  }
}

# Query virtual aggregated dashboard view (single pushdown SQL query)
query {
  studentDashboards {
    studentName
    departmentName
    gpa
  }
}

# Create a student via GraphQL mutation
mutation {
  createStudent(input: { name: "Grace Hopper", email: "grace@navy.mil", departmentId: 1 }) {
    id
    name
    version
  }
}
```
