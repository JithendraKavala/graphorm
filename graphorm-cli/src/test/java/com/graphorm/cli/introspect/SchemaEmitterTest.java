package com.graphorm.cli.introspect;

import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TASK-14.5: SchemaEmitter Unit Tests")
class SchemaEmitterTest {

    @Test
    @DisplayName("Should convert PostgreSQL types correctly")
    void shouldMapPostgresTypes() {
        assertThat(SchemaEmitter.mapPostgresType("integer", "int4")).isEqualTo("Int");
        assertThat(SchemaEmitter.mapPostgresType("bigint", "int8")).isEqualTo("BigInt");
        assertThat(SchemaEmitter.mapPostgresType("character varying", "varchar")).isEqualTo("String");
        assertThat(SchemaEmitter.mapPostgresType("text", "text")).isEqualTo("String");
        assertThat(SchemaEmitter.mapPostgresType("boolean", "bool")).isEqualTo("Boolean");
        assertThat(SchemaEmitter.mapPostgresType("numeric", "numeric")).isEqualTo("Decimal");
        assertThat(SchemaEmitter.mapPostgresType("double precision", "float8")).isEqualTo("Float");
        assertThat(SchemaEmitter.mapPostgresType("timestamp with time zone", "timestamptz")).isEqualTo("DateTime");
        assertThat(SchemaEmitter.mapPostgresType("date", "date")).isEqualTo("Date");
        assertThat(SchemaEmitter.mapPostgresType("uuid", "uuid")).isEqualTo("UUID");
        assertThat(SchemaEmitter.mapPostgresType("jsonb", "jsonb")).isEqualTo("Json");
        assertThat(SchemaEmitter.mapPostgresType("bytea", "bytea")).isEqualTo("Bytes");
    }

    @Test
    @DisplayName("Should handle case and plural conversions")
    void shouldHandleCaseAndPluralConversions() {
        assertThat(SchemaEmitter.toModelName("users")).isEqualTo("User");
        assertThat(SchemaEmitter.toModelName("departments")).isEqualTo("Department");
        assertThat(SchemaEmitter.toModelName("order_items")).isEqualTo("OrderItem");
        assertThat(SchemaEmitter.toModelName("categories")).isEqualTo("Category");

        assertThat(SchemaEmitter.toCamelCase("first_name")).isEqualTo("firstName");
        assertThat(SchemaEmitter.toCamelCase("department_id")).isEqualTo("departmentId");
        assertThat(SchemaEmitter.toCamelCase("id")).isEqualTo("id");

        assertThat(SchemaEmitter.toPlural("user")).isEqualTo("users");
        assertThat(SchemaEmitter.toPlural("category")).isEqualTo("categories");
    }

    @Test
    @DisplayName("Should emit valid GraphORM schema for models and database views")
    void shouldEmitValidSchemaForTablesAndViews() {
        // Table 1: users
        TableInfo usersTable = new TableInfo("users", false);
        usersTable.addColumn(new ColumnInfo("users", "id", "integer", "int4", false, "nextval('users_id_seq')", 1));
        usersTable.addColumn(new ColumnInfo("users", "email", "character varying", "varchar", false, null, 2));
        usersTable.addColumn(new ColumnInfo("users", "full_name", "character varying", "varchar", true, null, 3));
        usersTable.addColumn(new ColumnInfo("users", "created_at", "timestamp with time zone", "timestamptz", false, "now()", 4));
        usersTable.addPrimaryKeyColumn("id");
        usersTable.addUniqueColumn("email");

        // Table 2: posts
        TableInfo postsTable = new TableInfo("posts", false);
        postsTable.addColumn(new ColumnInfo("posts", "id", "integer", "int4", false, "nextval('posts_id_seq')", 1));
        postsTable.addColumn(new ColumnInfo("posts", "title", "character varying", "varchar", false, null, 2));
        postsTable.addColumn(new ColumnInfo("posts", "author_id", "integer", "int4", false, null, 3));
        postsTable.addPrimaryKeyColumn("id");
        postsTable.addForeignKey(new ForeignKeyInfo("fk_posts_author", "posts", "author_id", "users", "id"));

        // View: user_stats
        TableInfo userStatsView = new TableInfo("user_stats", true);
        userStatsView.addColumn(new ColumnInfo("user_stats", "user_id", "integer", "int4", false, null, 1));
        userStatsView.addColumn(new ColumnInfo("user_stats", "total_posts", "bigint", "int8", true, null, 2));

        String emittedSchema = SchemaEmitter.emit(List.of(usersTable, postsTable, userStatsView), "postgresql");

        assertThat(emittedSchema)
                .contains("datasource db")
                .contains("provider = \"postgresql\"")
                .contains("model User {")
                .contains("@@map(\"users\")")
                .contains("id")
                .contains("@id")
                .contains("@default(autoincrement())")
                .contains("email")
                .contains("@unique")
                .contains("fullName")
                .contains("String?")
                .contains("@map(\"full_name\")")
                .contains("posts")
                .contains("Post[]")
                .contains("model Post {")
                .contains("@@map(\"posts\")")
                .contains("authorId")
                .contains("@map(\"author_id\")")
                .contains("author")
                .contains("User")
                .contains("@relation(fields: [authorId], references: [id])")
                .contains("view UserStat {")
                .contains("@@view")
                .contains("@@map(\"user_stats\")");

        // Validate that the emitted schema parses and semantically validates cleanly!
        SchemaDocument doc = GraphOrmParserFacade.parse(emittedSchema);
        assertThat(doc.models()).hasSize(2);
        assertThat(doc.viewModels()).hasSize(1);

        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.isValid()).isTrue();
        assertThat(result.hasErrors()).isFalse();
    }
}
