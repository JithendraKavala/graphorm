# GraphORM: 5-Minute Quickstart

Get up and running with GraphORM in under 5 minutes. This tutorial guides you through creating a schema, generating type-safe Java 21 records and client accessors, bootstrapping a Spring Boot application, and querying your database both through code and automatic GraphQL endpoints.

---

## Prerequisites

- **Java**: OpenJDK 21 or later
- **Build Tool**: Maven 3.9+
- **Database**: PostgreSQL 15+ (local instance or Docker container)

---

## Step 1: Add Dependencies

Add the GraphORM Spring Boot Starter to your `pom.xml`:

```xml
<dependencies>
    <!-- GraphORM Spring Boot Starter -->
    <dependency>
        <groupId>com.graphorm</groupId>
        <artifactId>graphorm-spring-boot-starter</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>

    <!-- Spring Boot Starters -->
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
```

---

## Step 2: Define Your Schema (`schema.graphorm`)

Place your data model in `src/main/resources/schema.graphorm`:

```prisma
datasource db {
  provider = "postgresql"
  url      = "postgresql://localhost:5432/university_db"
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
  departmentId Int         @map("department_id")
  department   Department? @relation(fields: [departmentId], references: [id])

  @@map("students")
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

## Step 3: Generate Type-Safe Java Code

Run the GraphORM CLI to validate your schema and generate immutable Java 21 records, DTOs, and client accessors:

```bash
# Validate schema
graphorm validate --schema src/main/resources/schema.graphorm

# Generate Java 21 client code
graphorm generate \
  --schema src/main/resources/schema.graphorm \
  --output src/main/java \
  --package com.example.university.generated
```

This generates:
- **Models**: `Student.java`, `Department.java` (immutable Java 21 records with builders and `.with*()` methods)
- **Views**: `StudentDashboard.java` (strictly read-only projection record)
- **Inputs**: `StudentCreateInput.java`, `StudentUpdateInput.java` (strictly for regular models)
- **Filters**: `StudentFilter.java`, `DepartmentFilter.java` (fluent criteria builders)
- **Client**: `GraphOrmClient.java` (fluent database accessor API)

---

## Step 4: Configure `application.yml`

Configure your database and GraphORM in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: university-service
  datasource:
    url: jdbc:postgresql://localhost:5432/university_db
    username: postgres
    password: ""
  graphql:
    graphiql:
      enabled: true
      path: /graphiql

graphorm:
  schema-location: classpath:schema.graphorm
  dialect: postgresql
  sql-logging: true
  graphql:
    enabled: true
    auto-fetchers: true
  security:
    field-authorization-enabled: true
    depth-limit-enabled: true
    max-query-depth: 10
    complexity-limit-enabled: true
    max-query-complexity: 150
```

---

## Step 5: Write Application Code with Type-Safe Client API

Inject the generated `GraphOrmClient` to perform type-safe queries and mutations:

```java
package com.example.university.service;

import com.example.university.generated.client.GraphOrmClient;
import com.example.university.generated.input.StudentCreateInput;
import com.example.university.generated.model.Student;
import com.example.university.generated.model.StudentDashboard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UniversityService {

    private final GraphOrmClient db;

    public UniversityService(GraphOrmClient db) {
        this.db = db;
    }

    @Transactional
    public Student enrollStudent(String name, String email, int departmentId) {
        return db.student().create(StudentCreateInput.builder()
                .name(name)
                .email(email)
                .departmentId(departmentId)
                .build());
    }

    @Transactional(readOnly = true)
    public List<Student> getHonorStudents() {
        return db.student().findMany(q -> q
                .where(f -> f.gpa().gte(3.5))
                .orderBy(f -> f.name().asc())
                .limit(50)
        );
    }

    @Transactional(readOnly = true)
    public List<StudentDashboard> getDashboards() {
        return db.studentDashboard().findMany(q -> q
                .orderBy(f -> f.studentName().asc())
        );
    }
}
```

---

## Step 6: Query via GraphiQL

Launch your Spring Boot application and open your browser to `http://localhost:8080/graphiql`.

Execute a nested GraphQL query—GraphORM automatically resolves relationships using DataLoader batching (0 N+1 queries):

```graphql
query {
  students {
    id
    name
    email
    department {
      name
      code
    }
  }
  studentDashboards {
    studentId
    studentName
    departmentName
  }
}
```

Congratulations! You have successfully built and queried an end-to-end application with GraphORM!
