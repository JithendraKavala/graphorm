package com.graphorm.jdbc.ddl;

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
}
