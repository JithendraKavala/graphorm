package com.graphorm.jdbc.ddl.diff;

import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.IndexType;
import com.graphorm.core.metadata.ReferentialAction;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.DatabaseSchema;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationPlanTest {

    private final SqlDialect dialect = new PostgreSqlDialect();

    @Test
    @DisplayName("Should generate SQL in correct dependency order")
    void shouldGenerateSqlInDependencyOrder() {
        TableDefinition baseTable = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("obsolete").type("VARCHAR(255)").build())
                .primaryKeyColumns(List.of("id"))
                .build();

        DatabaseSchema baseline = DatabaseSchema.builder()
                .addTable(baseTable)
                .addEnum("UserRole", List.of("USER"))
                .build();

        TableDefinition targetUsers = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("email").type("VARCHAR(255)").nullable(false).build())
                .primaryKeyColumns(List.of("id"))
                .build();

        TableDefinition targetPosts = TableDefinition.builder("posts")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("user_id").type("INTEGER").nullable(false).build())
                .primaryKeyColumns(List.of("id"))
                .addForeignKey(ForeignKeyDefinition.builder("fk_posts_user")
                        .sourceTable("posts")
                        .sourceColumns(List.of("user_id"))
                        .targetTable("users")
                        .targetColumns(List.of("id"))
                        .onDelete(ReferentialAction.CASCADE)
                        .build())
                .addIndex(IndexMetadata.builder("idx_posts_user_id").fields("user_id").type(IndexType.BTREE).build())
                .build();

        DatabaseSchema target = DatabaseSchema.builder()
                .addTable(targetUsers)
                .addTable(targetPosts)
                .addEnum("UserRole", List.of("USER", "ADMIN"))
                .addEnum("PostStatus", List.of("DRAFT", "PUBLISHED"))
                .build();

        SchemaDiff diff = SchemaDiffer.diff(baseline, target);
        List<String> statements = MigrationPlan.generateSql(diff, dialect);

        assertThat(statements).isNotEmpty();

        // 1. Enum creation and alteration
        assertThat(statements).anyMatch(s -> s.contains("CREATE TYPE \"PostStatus\""));
        assertThat(statements).anyMatch(s -> s.contains("ALTER TYPE \"UserRole\" ADD VALUE 'ADMIN'"));

        // 2. Table creation for posts
        assertThat(statements).anyMatch(s -> s.contains("CREATE TABLE IF NOT EXISTS \"posts\""));

        // 3. Foreign key and index creation
        assertThat(statements).anyMatch(s -> s.contains("ALTER TABLE \"posts\" ADD CONSTRAINT \"fk_posts_user\""));
        assertThat(statements).anyMatch(s -> s.contains("CREATE INDEX IF NOT EXISTS \"idx_posts_user_id\""));

        // 4. Alter table users: add email and drop obsolete
        assertThat(statements).anyMatch(s -> s.contains("ALTER TABLE \"users\" ADD COLUMN \"email\""));
        assertThat(statements).anyMatch(s -> s.contains("ALTER TABLE \"users\" DROP COLUMN IF EXISTS \"obsolete\" CASCADE;"));
    }
}
