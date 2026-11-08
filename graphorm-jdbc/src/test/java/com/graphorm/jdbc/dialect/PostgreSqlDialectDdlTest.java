package com.graphorm.jdbc.dialect;

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
}
