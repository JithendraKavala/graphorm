package com.graphorm.jdbc.ddl;

import com.graphorm.core.metadata.DefaultValue;
import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.EnumMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.IndexType;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ReferentialAction;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.metadata.ViewModelMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseSchemaTest {

    @Test
    @DisplayName("Should transform ModelRegistry into DatabaseSchema correctly")
    void shouldTransformModelRegistry() {
        EnumMetadata roleEnum = EnumMetadata.builder("Role")
                .values("USER", "ADMIN")
                .build();

        ModelMetadata userModel = ModelMetadata.builder("User")
                .tableName("users")
                .addField(FieldMetadata.builder("id", Integer.class)
                        .primaryKey(true)
                        .generated(true)
                        .defaultValue("autoincrement()")
                        .build())
                .addField(FieldMetadata.builder("email", String.class)
                        .unique(true)
                        .nullable(false)
                        .build())
                .addField(FieldMetadata.builder("role")
                        .typeName("Role")
                        .nullable(false)
                        .defaultValue("'USER'")
                        .build())
                .addPrimaryKeyField("id")
                .addIndex(IndexMetadata.builder("idx_users_email")
                        .fields("email")
                        .unique(true)
                        .type(IndexType.BTREE)
                        .build())
                .addCheckConstraint(new CheckConstraintMetadata("chk_users_email", "email LIKE '%@%'"))
                .build();

        ModelMetadata postModel = ModelMetadata.builder("Post")
                .tableName("posts")
                .addField(FieldMetadata.builder("id", Integer.class)
                        .primaryKey(true)
                        .generated(true)
                        .build())
                .addField(FieldMetadata.builder("title", String.class)
                        .nullable(false)
                        .build())
                .addField(FieldMetadata.builder("authorId", Integer.class)
                        .dbColumnName("author_id")
                        .nullable(false)
                        .build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("author")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Post")
                        .targetModel("User")
                        .foreignKeyField("author_id")
                        .referenceField("id")
                        .onDelete(ReferentialAction.CASCADE)
                        .build())
                .build();

        ViewModelMetadata userSummaryView = ViewModelMetadata.builder("UserSummary")
                .viewName("user_summary")
                .sourceModel("User", "u")
                .addField(FieldMetadata.builder("id", Integer.class).build())
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addEnum(roleEnum)
                .addModel(userModel)
                .addModel(postModel)
                .addViewModel(userSummaryView)
                .build();

        DatabaseSchema schema = DatabaseSchema.fromModelRegistry(registry);

        // Verify enums
        assertThat(schema.enums()).containsKey("Role");
        assertThat(schema.enums().get("Role")).containsExactly("USER", "ADMIN");

        // Verify tables: views must be skipped
        assertThat(schema.tables()).containsOnlyKeys("users", "posts");
        assertThat(schema.hasTable("users")).isTrue();
        assertThat(schema.hasTable("posts")).isTrue();
        assertThat(schema.hasTable("user_summary")).isFalse();

        // Verify users table
        TableDefinition usersTable = schema.findTable("users").orElseThrow();
        assertThat(usersTable.primaryKeyColumns()).containsExactly("id");
        assertThat(usersTable.indexes()).hasSize(1);
        assertThat(usersTable.checkConstraints()).hasSize(1);
        assertThat(usersTable.findColumn("id")).isPresent();
        assertThat(usersTable.findColumn("id").get().isGenerated()).isTrue();
        assertThat(usersTable.findColumn("role").get().type()).isEqualTo("Role");

        // Verify posts table foreign key
        TableDefinition postsTable = schema.findTable("posts").orElseThrow();
        assertThat(postsTable.foreignKeys()).hasSize(1);
        ForeignKeyDefinition fk = postsTable.foreignKeys().get(0);
        assertThat(fk.sourceTable()).isEqualTo("posts");
        assertThat(fk.targetTable()).isEqualTo("users");
        assertThat(fk.sourceColumns()).containsExactly("author_id");
        assertThat(fk.targetColumns()).containsExactly("id");
        assertThat(fk.onDelete()).isEqualTo(ReferentialAction.CASCADE);
    }

    @Test
    @DisplayName("Should correctly map and render all default value types in ColumnDefinition")
    void shouldRenderAllDefaultValueTypesInColumnDefinition() {
        EnumMetadata roleEnum = EnumMetadata.builder("Role")
                .values("USER", "ADMIN")
                .build();

        ModelMetadata itemModel = ModelMetadata.builder("Item")
                .tableName("items")
                // Autoincrement
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).generated(true).defaultValue("autoincrement()").build())
                // Ordinary string
                .addField(FieldMetadata.builder("status", String.class).nullable(false).defaultValue(DefaultValue.ofString("ACTIVE")).build())
                // String with apostrophe
                .addField(FieldMetadata.builder("quote", String.class).nullable(false).defaultValue(DefaultValue.ofString("John's")).build())
                // Empty string
                .addField(FieldMetadata.builder("empty", String.class).nullable(false).defaultValue(DefaultValue.ofString("")).build())
                // Numerics
                .addField(FieldMetadata.builder("count", Integer.class).nullable(false).defaultValue(DefaultValue.ofNumeric("0")).build())
                .addField(FieldMetadata.builder("offset", Integer.class).nullable(false).defaultValue(DefaultValue.ofNumeric("-5")).build())
                .addField(FieldMetadata.builder("price", Double.class).nullable(false).defaultValue(DefaultValue.ofNumeric("9.99")).build())
                .addField(FieldMetadata.builder("negFloat", Double.class).nullable(false).defaultValue(DefaultValue.ofNumeric("-3.14")).build())
                // Booleans
                .addField(FieldMetadata.builder("active", Boolean.class).nullable(false).defaultValue(DefaultValue.ofBoolean(true)).build())
                .addField(FieldMetadata.builder("inactive", Boolean.class).nullable(false).defaultValue(DefaultValue.ofBoolean(false)).build())
                // Null
                .addField(FieldMetadata.builder("none", String.class).nullable(true).defaultValue(DefaultValue.ofNull()).build())
                // Expressions
                .addField(FieldMetadata.builder("createdAt", java.time.Instant.class).nullable(false).defaultValue(DefaultValue.ofExpression("now()")).build())
                .addField(FieldMetadata.builder("updatedAt", java.time.Instant.class).nullable(false).defaultValue(DefaultValue.ofExpression("CURRENT_TIMESTAMP")).build())
                .addField(FieldMetadata.builder("today", java.time.LocalDate.class).nullable(false).defaultValue(DefaultValue.ofExpression("CURRENT_DATE")).build())
                .addField(FieldMetadata.builder("token", String.class).nullable(false).defaultValue(DefaultValue.ofExpression("gen_random_uuid()")).build())
                // Enum defaults
                .addField(FieldMetadata.builder("role").typeName("Role").nullable(false).defaultValue(DefaultValue.ofString("USER")).build())
                // Already represented string literal (legacy/fallback)
                .addField(FieldMetadata.builder("legacyQuoted", String.class).nullable(false).defaultValue("'ACTIVE'").build())
                .addPrimaryKeyField("id")
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addEnum(roleEnum)
                .addModel(itemModel)
                .build();

        DatabaseSchema schema = DatabaseSchema.fromModelRegistry(registry);
        TableDefinition table = schema.findTable("items").orElseThrow();

        // Autoincrement: isGenerated=true, defaultValue=null
        ColumnDefinition idCol = table.findColumn("id").orElseThrow();
        assertThat(idCol.isGenerated()).isTrue();
        assertThat(idCol.defaultValue()).isNull();

        // Strings
        assertThat(table.findColumn("status").orElseThrow().defaultValue()).isEqualTo("'ACTIVE'");
        assertThat(table.findColumn("quote").orElseThrow().defaultValue()).isEqualTo("'John''s'");
        assertThat(table.findColumn("empty").orElseThrow().defaultValue()).isEqualTo("''");
        assertThat(table.findColumn("legacyQuoted").orElseThrow().defaultValue()).isEqualTo("'ACTIVE'");

        // Numerics (unquoted)
        assertThat(table.findColumn("count").orElseThrow().defaultValue()).isEqualTo("0");
        assertThat(table.findColumn("offset").orElseThrow().defaultValue()).isEqualTo("-5");
        assertThat(table.findColumn("price").orElseThrow().defaultValue()).isEqualTo("9.99");
        assertThat(table.findColumn("negFloat").orElseThrow().defaultValue()).isEqualTo("-3.14");

        // Booleans (TRUE / FALSE)
        assertThat(table.findColumn("active").orElseThrow().defaultValue()).isEqualTo("TRUE");
        assertThat(table.findColumn("inactive").orElseThrow().defaultValue()).isEqualTo("FALSE");

        // Null (unquoted NULL)
        assertThat(table.findColumn("none").orElseThrow().defaultValue()).isEqualTo("NULL");

        // Expressions (unquoted expressions)
        assertThat(table.findColumn("createdAt").orElseThrow().defaultValue()).isEqualTo("now()");
        assertThat(table.findColumn("updatedAt").orElseThrow().defaultValue()).isEqualTo("CURRENT_TIMESTAMP");
        assertThat(table.findColumn("today").orElseThrow().defaultValue()).isEqualTo("CURRENT_DATE");
        assertThat(table.findColumn("token").orElseThrow().defaultValue()).isEqualTo("gen_random_uuid()");

        // Enum default (quoted string for PostgreSQL enum)
        assertThat(table.findColumn("role").orElseThrow().defaultValue()).isEqualTo("'USER'");

        // Verify defaultValueDefinition() is preserved as source of truth
        assertThat(table.findColumn("status").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString("ACTIVE"));
        assertThat(table.findColumn("quote").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString("John's"));
        assertThat(table.findColumn("empty").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString(""));
        assertThat(table.findColumn("count").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNumeric("0"));
        assertThat(table.findColumn("offset").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNumeric("-5"));
        assertThat(table.findColumn("price").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNumeric("9.99"));
        assertThat(table.findColumn("negFloat").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNumeric("-3.14"));
        assertThat(table.findColumn("active").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofBoolean(true));
        assertThat(table.findColumn("inactive").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofBoolean(false));
        assertThat(table.findColumn("none").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNull());
        assertThat(table.findColumn("createdAt").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofExpression("now()"));
        assertThat(table.findColumn("updatedAt").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofExpression("CURRENT_TIMESTAMP"));
        assertThat(table.findColumn("today").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofExpression("CURRENT_DATE"));
        assertThat(table.findColumn("token").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofExpression("gen_random_uuid()"));
        assertThat(table.findColumn("role").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString("USER"));
    }

    @Test
    @DisplayName("Should preserve string 'null', 'true', 'false', exponent notation, and expressions with arguments")
    void shouldPreserveEdgeCasesInDatabaseSchema() {
        ModelMetadata model = ModelMetadata.builder("EdgeCaseModel")
                .tableName("edge_cases")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("strNull", String.class).nullable(false).defaultValue(DefaultValue.ofString("null")).build())
                .addField(FieldMetadata.builder("strTrue", String.class).nullable(false).defaultValue(DefaultValue.ofString("true")).build())
                .addField(FieldMetadata.builder("strFalse", String.class).nullable(false).defaultValue(DefaultValue.ofString("false")).build())
                .addField(FieldMetadata.builder("scientific", Double.class).nullable(false).defaultValue(DefaultValue.ofNumeric("1.5e-3")).build())
                .addField(FieldMetadata.builder("expLarge", Double.class).nullable(false).defaultValue(DefaultValue.ofNumeric("1e5")).build())
                .addField(FieldMetadata.builder("prefixed", String.class).nullable(false).defaultValue(DefaultValue.ofExpression("concat('USER-', 42)")).build())
                .addPrimaryKeyField("id")
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(model)
                .build();

        DatabaseSchema schema = DatabaseSchema.fromModelRegistry(registry);
        TableDefinition table = schema.findTable("edge_cases").orElseThrow();

        // String "null", "true", "false" rendered as quoted SQL strings
        assertThat(table.findColumn("strNull").orElseThrow().defaultValue()).isEqualTo("'null'");
        assertThat(table.findColumn("strNull").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString("null"));

        assertThat(table.findColumn("strTrue").orElseThrow().defaultValue()).isEqualTo("'true'");
        assertThat(table.findColumn("strTrue").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString("true"));

        assertThat(table.findColumn("strFalse").orElseThrow().defaultValue()).isEqualTo("'false'");
        assertThat(table.findColumn("strFalse").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofString("false"));

        // Exponent notation numeric preservation
        assertThat(table.findColumn("scientific").orElseThrow().defaultValue()).isEqualTo("1.5e-3");
        assertThat(table.findColumn("scientific").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNumeric("1.5e-3"));

        assertThat(table.findColumn("expLarge").orElseThrow().defaultValue()).isEqualTo("1e5");
        assertThat(table.findColumn("expLarge").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofNumeric("1e5"));

        // Expressions with typed arguments
        assertThat(table.findColumn("prefixed").orElseThrow().defaultValue()).isEqualTo("concat('USER-', 42)");
        assertThat(table.findColumn("prefixed").orElseThrow().defaultValueDefinition()).isEqualTo(DefaultValue.ofExpression("concat('USER-', 42)"));
    }

    @Test
    @DisplayName("Should produce deterministic schema and DDL output across repeated runs")
    void shouldProduceDeterministicOutputAcrossRepeatedRuns() {
        ModelMetadata users = ModelMetadata.builder("User")
                .tableName("users")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("name", String.class).nullable(false).defaultValue(DefaultValue.ofString("Anonymous")).build())
                .addField(FieldMetadata.builder("active", Boolean.class).nullable(false).defaultValue(DefaultValue.ofBoolean(true)).build())
                .addPrimaryKeyField("id")
                .build();

        ModelMetadata posts = ModelMetadata.builder("Post")
                .tableName("posts")
                .addField(FieldMetadata.builder("id", Integer.class).primaryKey(true).generated(true).build())
                .addField(FieldMetadata.builder("title", String.class).nullable(false).defaultValue(DefaultValue.ofString("Draft")).build())
                .addField(FieldMetadata.builder("views", Integer.class).nullable(false).defaultValue(DefaultValue.ofNumeric("0")).build())
                .addPrimaryKeyField("id")
                .build();

        ModelRegistry registry = ModelRegistry.builder()
                .addModel(users)
                .addModel(posts)
                .build();

        com.graphorm.jdbc.dialect.PostgreSqlDialect pgDialect = new com.graphorm.jdbc.dialect.PostgreSqlDialect();

        String baselineDdl = null;
        for (int i = 0; i < 10; i++) {
            DatabaseSchema schema = DatabaseSchema.fromModelRegistry(registry, pgDialect);
            StringBuilder sb = new StringBuilder();
            for (TableDefinition tbl : schema.tables().values()) {
                sb.append(pgDialect.buildCreateTableSql(tbl)).append("\n");
            }
            String currentDdl = sb.toString();

            if (baselineDdl == null) {
                baselineDdl = currentDdl;
            } else {
                assertThat(currentDdl).isEqualTo(baselineDdl);
            }
        }
    }
}
