package com.graphorm.schema.validation;

import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SchemaValidator DDL (Index, Unique, Referential Action, Check) Tests")
class SchemaValidatorDdlTest {

    @Test
    @DisplayName("Should pass valid indexes, unique constraints, and referential actions")
    void shouldPassValidDdlSchema() {
        String schema = """
            datasource db {
              provider = "postgresql"
              url = env("DATABASE_URL")
            }

            model Department {
              id   Int    @id
              name String
              students Student[]
            }

            model Student {
              id           Int        @id
              email        String     @unique
              firstName    String     @index
              lastName     String
              age          Int
              departmentId Int
              department   Department @relation(fields: [departmentId], references: [id], onDelete: Cascade, onUpdate: Restrict)

              @@index([firstName, lastName], name: "idx_student_full_name", type: "BTREE")
              @@unique([firstName, lastName])
              @@check("age >= 18", name: "chk_student_age")
              @@schema("university")
            }
            """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Should report error if @@index references non-existent field")
    void shouldReportErrorForUnknownIndexField() {
        String schema = """
            datasource db {
              provider = "postgresql"
              url = env("DATABASE_URL")
            }

            model Student {
              id    Int    @id
              email String

              @@index([nonExistentField])
            }
            """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).message()).contains("Field 'nonExistentField' specified in @@index does not exist");
    }

    @Test
    @DisplayName("Should report error if @@unique references non-existent field")
    void shouldReportErrorForUnknownUniqueField() {
        String schema = """
            datasource db {
              provider = "postgresql"
              url = env("DATABASE_URL")
            }

            model Student {
              id    Int    @id
              email String

              @@unique([missingCol1, missingCol2])
            }
            """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).message()).contains("Field 'missingCol1' specified in @@unique does not exist");
    }

    @Test
    @DisplayName("Should report error for invalid index type")
    void shouldReportErrorForInvalidIndexType() {
        String schema = """
            datasource db {
              provider = "postgresql"
              url = env("DATABASE_URL")
            }

            model Student {
              id    Int    @id
              email String

              @@index([email], type: "INVALID_TYPE")
            }
            """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).message()).contains("Invalid index type 'INVALID_TYPE'");
    }

    @Test
    @DisplayName("Should report error for invalid onDelete or onUpdate action")
    void shouldReportErrorForInvalidReferentialAction() {
        String schema = """
            datasource db {
              provider = "postgresql"
              url = env("DATABASE_URL")
            }

            model Department {
              id Int @id
            }

            model Student {
              id           Int        @id
              departmentId Int
              department   Department @relation(fields: [departmentId], references: [id], onDelete: "DESTROY_ALL")
            }
            """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().get(0).message()).contains("Invalid onDelete action 'DESTROY_ALL'");
    }
}
