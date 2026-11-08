package com.graphorm.schema.transformer;

import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.IndexType;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ReferentialAction;
import com.graphorm.core.metadata.RelationMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchemaToMetadataTransformer DDL (Indexes, Checks, Referential Actions) Tests")
class SchemaToMetadataDdlTest {

    @Test
    @DisplayName("Should transform indexes, check constraints, schema namespace, and referential actions")
    void shouldTransformDdlMetadata() {
        String schema = """
            datasource db {
              provider = "postgresql"
              url = env("DATABASE_URL")
            }

            model Department {
              id   Int    @id
              name String
            }

            model Student {
              id           Int        @id
              email        String     @unique
              firstName    String     @index
              lastName     String
              age          Int
              departmentId Int
              department   Department @relation(fields: [departmentId], references: [id], onDelete: Cascade, onUpdate: SetNull)

              @@index([firstName, lastName], name: "idx_student_names", unique: false, type: "BTREE")
              @@check("age >= 18", name: "chk_age_min")
              @@schema("custom_schema")
            }
            """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ModelMetadata student = registry.getModel("Student");

        assertThat(student.schemaName()).isEqualTo("custom_schema");

        // Indexes
        assertThat(student.indexes()).hasSize(2);
        IndexMetadata singleIdx = student.findIndex("idx_student_firstName").orElseThrow();
        assertThat(singleIdx.fields()).containsExactly("firstName");

        IndexMetadata compoundIdx = student.findIndex("idx_student_names").orElseThrow();
        assertThat(compoundIdx.fields()).containsExactly("firstName", "lastName");
        assertThat(compoundIdx.isUnique()).isFalse();
        assertThat(compoundIdx.type()).isEqualTo(IndexType.BTREE);

        // Check constraints
        assertThat(student.checkConstraints()).hasSize(1);
        assertThat(student.findCheckConstraint("chk_age_min")).isPresent();
        assertThat(student.findCheckConstraint("chk_age_min").get().expression()).isEqualTo("age >= 18");

        // Referential actions
        RelationMetadata rel = student.getRelation("department");
        assertThat(rel.onDelete()).isEqualTo(ReferentialAction.CASCADE);
        assertThat(rel.onUpdate()).isEqualTo(ReferentialAction.SET_NULL);
    }
}
