package com.graphorm.jdbc.dialect;

import com.graphorm.core.metadata.DefaultValue;
import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.IndexType;
import com.graphorm.core.metadata.ReferentialAction;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgreSqlDialect DDL Generation Unit Tests")
class PostgreSqlDialectDdlTest {

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    @Test
    @DisplayName("Should build CREATE TABLE statement with columns, PK, unique, and check constraints")
    void shouldBuildCreateTableSql() {
        TableDefinition table = TableDefinition.builder("students")
                .schemaName("public")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").generated(true).primaryKey(true).nullable(false).build())
                .addColumn(ColumnDefinition.builder("email").type("VARCHAR(255)").unique(true).nullable(false).build())
                .addColumn(ColumnDefinition.builder("status").type("VARCHAR(50)").defaultValue("'ACTIVE'").nullable(false).build())
                .addColumn(ColumnDefinition.builder("created_at").type("TIMESTAMPTZ").defaultValue("CURRENT_TIMESTAMP").nullable(false).build())
                .addPrimaryKeyColumn("id")
                .addCheckConstraint(new CheckConstraintMetadata("chk_student_status", "status IN ('ACTIVE', 'INACTIVE')"))
                .build();

        String sql = dialect.buildCreateTableSql(table);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS \"public\".\"students\" (");
        assertThat(sql).contains("\"id\" SERIAL PRIMARY KEY");
        assertThat(sql).contains("\"email\" VARCHAR(255) NOT NULL UNIQUE");
        assertThat(sql).contains("\"status\" VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("\"created_at\" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
        assertThat(sql).contains("CONSTRAINT \"chk_student_status\" CHECK (status IN ('ACTIVE', 'INACTIVE'))");
    }

    @Test
    @DisplayName("Should build CREATE TABLE with composite primary key and compound unique constraint")
    void shouldBuildCreateTableWithCompositePkAndUnique() {
        TableDefinition table = TableDefinition.builder("enrollments")
                .addColumn(ColumnDefinition.builder("student_id").type("INTEGER").nullable(false).build())
                .addColumn(ColumnDefinition.builder("course_id").type("INTEGER").nullable(false).build())
                .addColumn(ColumnDefinition.builder("semester").type("VARCHAR(20)").nullable(false).build())
                .addPrimaryKeyColumn("student_id")
                .addPrimaryKeyColumn("course_id")
                .addUniqueConstraint(List.of("student_id", "course_id", "semester"))
                .build();

        String sql = dialect.buildCreateTableSql(table);

        assertThat(sql).contains("CONSTRAINT \"pk_enrollments\" PRIMARY KEY (\"student_id\", \"course_id\")");
        assertThat(sql).contains("CONSTRAINT \"uq_enrollments_student_id_course_id_semester\" UNIQUE (\"student_id\", \"course_id\", \"semester\")");
    }

    @Test
    @DisplayName("Should build ALTER TABLE ADD/DROP COLUMN statements")
    void shouldBuildAlterColumnStatements() {
        ColumnDefinition newCol = ColumnDefinition.builder("gpa")
                .type("NUMERIC(3, 2)")
                .nullable(true)
                .defaultValue("0.00")
                .build();

        String addCol = dialect.buildAddColumnSql("students", newCol);
        assertThat(addCol).isEqualTo("ALTER TABLE \"students\" ADD COLUMN \"gpa\" NUMERIC(3, 2) DEFAULT 0.00;");

        String dropCol = dialect.buildDropColumnSql("students", "gpa");
        assertThat(dropCol).isEqualTo("ALTER TABLE \"students\" DROP COLUMN IF EXISTS \"gpa\" CASCADE;");
    }

    @Test
    @DisplayName("Should build CREATE INDEX and DROP INDEX statements")
    void shouldBuildIndexStatements() {
        IndexMetadata idx = IndexMetadata.builder("idx_students_email_status")
                .fields(List.of("email", "status"))
                .unique(true)
                .type(IndexType.BTREE)
                .build();

        String createIdx = dialect.buildCreateIndexSql(idx, "students");
        assertThat(createIdx).isEqualTo("CREATE UNIQUE INDEX IF NOT EXISTS \"idx_students_email_status\" ON \"students\" USING btree (\"email\", \"status\");");

        String dropIdx = dialect.buildDropIndexSql("idx_students_email_status");
        assertThat(dropIdx).isEqualTo("DROP INDEX IF EXISTS \"idx_students_email_status\" CASCADE;");
    }

    @Test
    @DisplayName("Should build ADD FOREIGN KEY statement with referential actions")
    void shouldBuildAddForeignKeySql() {
        ForeignKeyDefinition fk = ForeignKeyDefinition.builder("fk_students_department")
                .sourceTable("students")
                .sourceColumns(List.of("dept_id"))
                .targetTable("departments")
                .targetColumns(List.of("id"))
                .onDelete(ReferentialAction.CASCADE)
                .onUpdate(ReferentialAction.RESTRICT)
                .build();

        String addFk = dialect.buildAddForeignKeySql(fk);
        assertThat(addFk).isEqualTo("ALTER TABLE \"students\" ADD CONSTRAINT \"fk_students_department\" FOREIGN KEY (\"dept_id\") REFERENCES \"departments\" (\"id\") ON DELETE CASCADE ON UPDATE RESTRICT;");
    }

    @Test
    @DisplayName("Should build CREATE and DROP ENUM statements")
    void shouldBuildEnumStatements() {
        String createEnum = dialect.buildCreateEnumSql("student_status", List.of("ENROLLED", "GRADUATED", "SUSPENDED"));
        assertThat(createEnum).contains("CREATE TYPE \"student_status\" AS ENUM ('ENROLLED', 'GRADUATED', 'SUSPENDED')");

        String dropEnum = dialect.buildDropEnumSql("student_status");
        assertThat(dropEnum).isEqualTo("DROP TYPE IF EXISTS \"student_status\" CASCADE;");
    }

    @Test
    @DisplayName("Should build advisory lock and unlock statements")
    void shouldBuildAdvisoryLockStatements() {
        assertThat(dialect.buildAdvisoryLockSql(12345678L)).isEqualTo("SELECT pg_advisory_lock(12345678);");
        assertThat(dialect.buildAdvisoryUnlockSql(12345678L)).isEqualTo("SELECT pg_advisory_unlock(12345678);");
    }

    @Test
    @DisplayName("Should generate correct PostgreSQL DDL for all supported default value types")
    void shouldGenerateDdlForAllDefaultValueTypes() {
        TableDefinition table = TableDefinition.builder("defaults_showcase")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").generated(true).primaryKey(true).nullable(false).build())
                .addColumn(ColumnDefinition.builder("status").type("VARCHAR(50)").defaultValue("'ACTIVE'").nullable(false).build())
                .addColumn(ColumnDefinition.builder("quote").type("VARCHAR(255)").defaultValue("'John''s'").nullable(false).build())
                .addColumn(ColumnDefinition.builder("empty").type("VARCHAR(10)").defaultValue("''").nullable(false).build())
                .addColumn(ColumnDefinition.builder("count").type("INTEGER").defaultValue("0").nullable(false).build())
                .addColumn(ColumnDefinition.builder("offset").type("INTEGER").defaultValue("-5").nullable(false).build())
                .addColumn(ColumnDefinition.builder("price").type("NUMERIC(10, 2)").defaultValue("9.99").nullable(false).build())
                .addColumn(ColumnDefinition.builder("active").type("BOOLEAN").defaultValue("TRUE").nullable(false).build())
                .addColumn(ColumnDefinition.builder("inactive").type("BOOLEAN").defaultValue("FALSE").nullable(false).build())
                .addColumn(ColumnDefinition.builder("none").type("TEXT").defaultValue("NULL").nullable(true).build())
                .addColumn(ColumnDefinition.builder("created_at").type("TIMESTAMPTZ").defaultValue("now()").nullable(false).build())
                .addColumn(ColumnDefinition.builder("updated_at").type("TIMESTAMPTZ").defaultValue("CURRENT_TIMESTAMP").nullable(false).build())
                .addColumn(ColumnDefinition.builder("today").type("DATE").defaultValue("CURRENT_DATE").nullable(false).build())
                .addColumn(ColumnDefinition.builder("token").type("UUID").defaultValue("gen_random_uuid()").nullable(false).build())
                .addColumn(ColumnDefinition.builder("role").type("\"UserRole\"").defaultValue("'USER'").nullable(false).build())
                // Columns created with typed DefaultValue directly (verifying dialect rendering from defaultValueDefinition)
                .addColumn(ColumnDefinition.builder("str_null").type("VARCHAR(50)").defaultValueDefinition(DefaultValue.ofString("null")).nullable(false).build())
                .addColumn(ColumnDefinition.builder("str_true").type("VARCHAR(50)").defaultValueDefinition(DefaultValue.ofString("true")).nullable(false).build())
                .addColumn(ColumnDefinition.builder("str_false").type("VARCHAR(50)").defaultValueDefinition(DefaultValue.ofString("false")).nullable(false).build())
                .addColumn(ColumnDefinition.builder("scientific").type("REAL").defaultValueDefinition(DefaultValue.ofNumeric("1.5e-3")).nullable(false).build())
                .addColumn(ColumnDefinition.builder("exp_large").type("REAL").defaultValueDefinition(DefaultValue.ofNumeric("1e5")).nullable(false).build())
                .addColumn(ColumnDefinition.builder("typed_null").type("TEXT").defaultValueDefinition(DefaultValue.ofNull()).nullable(true).build())
                .addColumn(ColumnDefinition.builder("typed_active").type("BOOLEAN").defaultValueDefinition(DefaultValue.ofBoolean(true)).nullable(false).build())
                .addColumn(ColumnDefinition.builder("typed_func").type("VARCHAR(100)").defaultValueDefinition(DefaultValue.ofExpression("concat('USER-', 42)")).nullable(false).build())
                .addPrimaryKeyColumn("id")
                .build();

        String sql = dialect.buildCreateTableSql(table);

        assertThat(sql).contains("\"id\" SERIAL PRIMARY KEY");
        assertThat(sql).contains("\"status\" VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'");
        assertThat(sql).contains("\"quote\" VARCHAR(255) NOT NULL DEFAULT 'John''s'");
        assertThat(sql).contains("\"empty\" VARCHAR(10) NOT NULL DEFAULT ''");
        assertThat(sql).contains("\"count\" INTEGER NOT NULL DEFAULT 0");
        assertThat(sql).contains("\"offset\" INTEGER NOT NULL DEFAULT -5");
        assertThat(sql).contains("\"price\" NUMERIC(10, 2) NOT NULL DEFAULT 9.99");
        assertThat(sql).contains("\"active\" BOOLEAN NOT NULL DEFAULT TRUE");
        assertThat(sql).contains("\"inactive\" BOOLEAN NOT NULL DEFAULT FALSE");
        assertThat(sql).contains("\"none\" TEXT DEFAULT NULL");
        assertThat(sql).contains("\"created_at\" TIMESTAMPTZ NOT NULL DEFAULT now()");
        assertThat(sql).contains("\"updated_at\" TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP");
        assertThat(sql).contains("\"today\" DATE NOT NULL DEFAULT CURRENT_DATE");
        assertThat(sql).contains("\"token\" UUID NOT NULL DEFAULT gen_random_uuid()");
        assertThat(sql).contains("\"role\" \"UserRole\" NOT NULL DEFAULT 'USER'");
        assertThat(sql).contains("\"str_null\" VARCHAR(50) NOT NULL DEFAULT 'null'");
        assertThat(sql).contains("\"str_true\" VARCHAR(50) NOT NULL DEFAULT 'true'");
        assertThat(sql).contains("\"str_false\" VARCHAR(50) NOT NULL DEFAULT 'false'");
        assertThat(sql).contains("\"scientific\" REAL NOT NULL DEFAULT 1.5e-3");
        assertThat(sql).contains("\"exp_large\" REAL NOT NULL DEFAULT 1e5");
        assertThat(sql).contains("\"typed_null\" TEXT DEFAULT NULL");
        assertThat(sql).contains("\"typed_active\" BOOLEAN NOT NULL DEFAULT TRUE");
        assertThat(sql).contains("\"typed_func\" VARCHAR(100) NOT NULL DEFAULT concat('USER-', 42)");
    }

    @Test
    @DisplayName("Should build ALTER TABLE ALTER COLUMN SET DEFAULT with typed DefaultValue")
    void shouldBuildAlterColumnDefaultWithTypedDefaultValue() {
        assertThat(dialect.buildAlterColumnDefaultSql("users", "status", DefaultValue.ofString("INACTIVE")))
                .isEqualTo("ALTER TABLE \"users\" ALTER COLUMN \"status\" SET DEFAULT 'INACTIVE';");
        assertThat(dialect.buildAlterColumnDefaultSql("users", "quote", DefaultValue.ofString("It's ok")))
                .isEqualTo("ALTER TABLE \"users\" ALTER COLUMN \"quote\" SET DEFAULT 'It''s ok';");
        assertThat(dialect.buildAlterColumnDefaultSql("users", "active", DefaultValue.ofBoolean(true)))
                .isEqualTo("ALTER TABLE \"users\" ALTER COLUMN \"active\" SET DEFAULT TRUE;");
        assertThat(dialect.buildAlterColumnDefaultSql("users", "count", DefaultValue.ofNumeric("100")))
                .isEqualTo("ALTER TABLE \"users\" ALTER COLUMN \"count\" SET DEFAULT 100;");
        assertThat(dialect.buildAlterColumnDefaultSql("users", "created_at", DefaultValue.ofExpression("CURRENT_TIMESTAMP")))
                .isEqualTo("ALTER TABLE \"users\" ALTER COLUMN \"created_at\" SET DEFAULT CURRENT_TIMESTAMP;");
        assertThat(dialect.buildAlterColumnDefaultSql("users", "none", DefaultValue.ofNull()))
                .isEqualTo("ALTER TABLE \"users\" ALTER COLUMN \"none\" SET DEFAULT NULL;");
    }

    @Test
    @DisplayName("Should render string literals safely without quote-detection heuristics")
    void shouldRenderStringLiteralsSafelyWithoutQuoteHeuristics() {
        // John's book -> 'John''s book'
        assertThat(dialect.renderStringLiteral("John's book")).isEqualTo("'John''s book'");
        assertThat(dialect.renderDefaultValue(DefaultValue.ofString("John's book"), "VARCHAR(255)")).isEqualTo("'John''s book'");

        // Empty string -> ''
        assertThat(dialect.renderStringLiteral("")).isEqualTo("''");
        assertThat(dialect.renderDefaultValue(DefaultValue.ofString(""), "VARCHAR(10)")).isEqualTo("''");

        // String containing only a single quote -> ''''
        assertThat(dialect.renderStringLiteral("'")).isEqualTo("''''");
        assertThat(dialect.renderDefaultValue(DefaultValue.ofString("'"), "VARCHAR(10)")).isEqualTo("''''");

        // String that already looks quoted, such as 'ACTIVE', must be escaped and quoted as string content
        assertThat(dialect.renderStringLiteral("'ACTIVE'")).isEqualTo("'''ACTIVE'''");
        assertThat(dialect.renderDefaultValue(DefaultValue.ofString("'ACTIVE'"), "VARCHAR(50)")).isEqualTo("'''ACTIVE'''");

        // Malicious-looking string such as ' OR 1=1 --, which must remain a safely quoted string literal
        assertThat(dialect.renderStringLiteral("' OR 1=1 --")).isEqualTo("''' OR 1=1 --'");
        assertThat(dialect.renderDefaultValue(DefaultValue.ofString("' OR 1=1 --"), "VARCHAR(100)")).isEqualTo("''' OR 1=1 --'");

        // Unicode support
        assertThat(dialect.renderStringLiteral("Café ☕ 日本語")).isEqualTo("'Café ☕ 日本語'");

        // Null handling
        assertThat(dialect.renderStringLiteral(null)).isEqualTo("NULL");

        // Keep raw SQL expressions separate from string literals
        assertThat(dialect.renderDefaultValue(DefaultValue.ofExpression("CURRENT_TIMESTAMP"), "TIMESTAMPTZ")).isEqualTo("CURRENT_TIMESTAMP");
        assertThat(dialect.renderDefaultValue(DefaultValue.ofExpression("now()"), "TIMESTAMPTZ")).isEqualTo("now()");
    }
}
