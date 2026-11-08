package com.graphorm.schema.lint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaLinterTest {

    @Test
    @DisplayName("SchemaLinter flags non-PascalCase models and non-camelCase fields")
    void flagsNamingConventions() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model user_profile {
                  ID        String @id
                  User_Name String
                }
                """;

        LintResult result = SchemaLinter.lint(schema);
        assertThat(result.isClean()).isFalse();

        assertThat(result.diagnostics())
                .anyMatch(d -> d.ruleName().equals(SchemaLinter.RULE_NAMING_CONVENTION) && d.message().contains("user_profile"));
        assertThat(result.diagnostics())
                .anyMatch(d -> d.ruleName().equals(SchemaLinter.RULE_NAMING_CONVENTION) && d.message().contains("User_Name"));
    }

    @Test
    @DisplayName("SchemaLinter flags foreign keys missing an index or unique constraint")
    void flagsMissingForeignKeyIndex() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model Department {
                  id String @id
                }

                model Employee {
                  id           String     @id
                  deptId       String
                  department   Department @relation(fields: [deptId], references: [id])
                }
                """;

        LintResult result = SchemaLinter.lint(schema);
        assertThat(result.isClean()).isFalse();

        assertThat(result.diagnostics())
                .anyMatch(d -> d.ruleName().equals(SchemaLinter.RULE_MISSING_FK_INDEX) && d.message().contains("deptId"));
    }

    @Test
    @DisplayName("SchemaLinter does not flag foreign keys that have an index")
    void passesWhenForeignKeyIsIndexed() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model Department {
                  id String @id
                }

                model Employee {
                  id           String     @id
                  deptId       String
                  department   Department @relation(fields: [deptId], references: [id])
                  @@index([deptId])
                }
                """;

        LintResult result = SchemaLinter.lint(schema);
        assertThat(result.diagnostics())
                .noneMatch(d -> d.ruleName().equals(SchemaLinter.RULE_MISSING_FK_INDEX));
    }

    @Test
    @DisplayName("SchemaLinter flags redundant @unique on @id")
    void flagsRedundantAnnotations() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model Account {
                  id String @id @unique
                }
                """;

        LintResult result = SchemaLinter.lint(schema);
        assertThat(result.diagnostics())
                .anyMatch(d -> d.ruleName().equals(SchemaLinter.RULE_REDUNDANT_ANNOTATION) && d.message().contains("Redundant @unique annotation on primary key field"));
    }
}
