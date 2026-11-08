package com.graphorm.schema.validation;

import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticLockingValidationTest {

    @Test
    @DisplayName("Valid model with @version Int passes schema validation")
    void validVersionFieldPasses() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model Product {
                  id      String @id
                  version Int    @version @default(1)
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Model with multiple @version fields fails schema validation")
    void multipleVersionFieldsFails() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model Product {
                  id       String @id
                  version1 Int    @version @default(1)
                  version2 Int    @version @default(1)
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(d -> d.message().contains("cannot have multiple @version fields"));
    }

    @Test
    @DisplayName("Model with non-numeric / non-datetime @version field fails validation")
    void invalidVersionFieldTypeFails() {
        String schema = """
                datasource db {
                  provider = "postgresql"
                  url      = env("DATABASE_URL")
                }

                model Product {
                  id      String @id
                  version String @version
                }
                """;

        SchemaDocument doc = GraphOrmParserFacade.parse(schema);
        ValidationResult result = SchemaValidator.validate(doc);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(d -> d.message().contains("annotated with @version must be of type Int, BigInt, or DateTime"));
    }
}
