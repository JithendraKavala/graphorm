# GraphORM CLI Reference

The GraphORM Command Line Interface (`graphorm-cli`) is a standalone developer tool built on Picocli for scaffolding projects, validating schemas, generating Java client code, and introspecting existing PostgreSQL databases.

---

## 1. Installation & Execution

### Running via Maven
Execute directly within any Maven project:
```bash
mvn exec:java -pl graphorm-cli \
  -Dexec.mainClass="com.graphorm.cli.GraphOrmCli" \
  -Dexec.args="<command> [options]"
```

### Running Standalone JAR
```bash
java -jar graphorm-cli-0.1.0-SNAPSHOT.jar <command> [options]
```

### Display Help and Version
```bash
graphorm --help
graphorm --version
```

Output:
```
  ____                 _     ___  ____  __  __
 / ___|_ __ __ _ _ __ | |__ / _ \|  _ \|  \/  |
| |  _| '__/ _` | '_ \| '_ \ | | | |_) | |\/| |
| |_| | | | (_| | |_) | | | | |_| |  _ <| |  | |
 \____|_|  \__,_| .__/|_| |_|\___/|_| \_\_|  |_|
                |_|
GraphORM CLI 0.1.0 (Java 21)
```

---

## 2. Command Index

| Command | Description |
|---|---|
| [`init`](#3-graphorm-init) | Scaffolds a starter GraphORM schema and configuration |
| [`validate`](#4-graphorm-validate) | Validates schema syntax, types, relations, and view invariants |
| [`generate`](#5-graphorm-generate) | Generates Java 21 records, DTOs, criteria filters, and client APIs |
| [`introspect`](#6-graphorm-introspect) | Reverse-engineers a PostgreSQL database into `schema.graphorm` |
| [`migrate`](#7-graphorm-migrate) | Schema lifecycle, versioned migrations, status, drift verification, and diff |

---

## 3. `graphorm init`

Scaffolds a new starter GraphORM schema and configuration properties file.

### Usage
```bash
graphorm init [--directory <dir>] [--force]
```

### Options
- `-d, --directory <dir>`: Target directory where files will be created (defaults to current working directory).
- `-f, --force`: Overwrite existing files if they already exist.

### Output Files
- `schema.graphorm`: Sample schema with `User`, `Post`, and `UserSummary` view model.
- `graphorm.properties`: Default configuration settings.

---

## 4. `graphorm validate`

Parses and semantically validates a `.graphorm` schema file.

### Usage
```bash
graphorm validate [-s, --schema <file>]
```

### Options
- `-s, --schema <file>`: Path to the schema file (defaults to `schema.graphorm`).

### Exit Codes
- `0`: Schema is completely valid.
- `1`: Syntax or semantic validation error occurred.

### Example Output
```
Schema 'schema.graphorm' is valid!
  Summary: 5 model(s), 2 view(s), 0 composite type(s), 0 enum(s)
```

If an error is found:
```
Validation failed with 1 diagnostic error(s):
  schema.graphorm:18:5: Model 'Student' does not declare a primary key (@id)
    18 | model Student {
       | ^^^^^
```

---

## 5. `graphorm generate`

Translates a `.graphorm` schema into strongly typed Java 21 source files using JavaPoet.

### Usage
```bash
graphorm generate [-s, --schema <file>] [-o, --output <dir>] [-p, --package <pkg>] [--clean]
```

### Options
- `-s, --schema <file>`: Path to input schema file (defaults to `schema.graphorm`).
- `-o, --output <dir>`: Target source root directory (defaults to `src/main/java`).
- `-p, --package <pkg>`: Base Java package name (defaults to `com.graphorm.generated`).
- `--clean`: Clean existing files in target directory before generation.

### Generated Artifacts
For every model:
- `model/<Model>.java`: Java 21 immutable record with builder and component with-methods.
- `input/<Model>CreateInput.java`: Strongly typed creation DTO.
- `input/<Model>UpdateInput.java`: Strongly typed update DTO.
- `filter/<Model>Filter.java`: Fluent criteria and comparison filter builder.
- `metadata/<Model>Metadata.java`: Compile-time column and table descriptors.
- `client/<Model>Client.java`: Model CRUD accessor implementing `ModelAccessor`.

For every view model:
- `model/<View>.java`: Strictly read-only projection record (zero mutation methods).
- `filter/<View>Filter.java`: Fluent criteria filter builder.
- `metadata/<View>Metadata.java`: View projection descriptors.
- `client/<View>Client.java`: Read-only view accessor (strictly excludes write mutations).

Root:
- `client/GraphOrmClient.java`: Unified Prisma-style client aggregating all entity accessors.

---

## 6. `graphorm introspect`

Connects to a live PostgreSQL database, inspects system catalogs (`information_schema` and `pg_catalog`), and emits a complete, formatted `schema.graphorm` DSL file.

### Usage
```bash
graphorm introspect --url <jdbc-url> --user <user> [--password <pwd>] [-o, --output <file>]
```

### Options
- `--url <jdbc-url>`: JDBC database connection URL (e.g. `jdbc:postgresql://localhost:5432/mydb`).
- `--user <username>`: Database username.
- `--password <password>`: Database password (defaults to empty string).
- `-o, --output <file>`: Output file path for generated schema (defaults to stdout or `schema.graphorm`).

### Reverse-Engineered Features
- Automatically identifies tables, columns, nullability, and data types.
- Discovers primary keys and marks them with `@id`.
- Discovers foreign keys and translates them to `@relation(fields: [...], references: [...])`.
- Discovers physical views in PostgreSQL and translates them into `view <Name> { ... @@view @@map("<view_name>") }`.

---

## 7. `graphorm migrate`

Provides end-to-end schema lifecycle and migration management, including differential DDL generation, versioned migration ledgers with SHA-256 tamper verification, PostgreSQL advisory locking, live drift detection, and corrective SQL diff generation.

### Subcommands

| Subcommand | Description |
|---|---|
| `migrate create <name>` | Compares `schema.graphorm` against the live database and generates a timestamped migration script |
| `migrate apply` | Applies all pending migrations within transactional boundaries under advisory lock |
| `migrate status` | Displays the status of applied, pending, and tampered migrations |
| `migrate verify` | Compares live database schema against `schema.graphorm` to verify zero drift |
| `migrate diff` | Computes and prints corrective SQL DDL without generating migration files |

### Global Options for Migrate Commands
- `-d, --migrations-dir <dir>`: Directory storing migration files (default: `migrations`).
- `-s, --schema <file>`: Path to GraphORM schema file (default: `schema.graphorm`).
- `--url <jdbc-url>`: Database JDBC URL (or defaults to `DATABASE_URL` environment variable).
- `-u, --user <username>`: Database user (default: `postgres`).
- `-p, --password <password>`: Database password (default: empty).
- `--db-schema <schema>`: Database schema name (default: `public`).

### Examples

#### 1. Create a migration from schema changes
```bash
graphorm migrate create add_user_profiles -s schema.graphorm
```

#### 2. Inspect migration status and checksum integrity
```bash
graphorm migrate status
```

#### 3. Apply pending migrations
```bash
graphorm migrate apply
```

#### 4. Verify zero schema drift in CI/CD pipeline
```bash
graphorm migrate verify -s schema.graphorm
```
*Returns exit code 0 if in sync, exit code 2 if schema drift is detected.*

#### 5. Generate corrective diff DDL
```bash
graphorm migrate diff -s schema.graphorm
```
