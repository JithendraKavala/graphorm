package com.graphorm.schema.validation;

import com.graphorm.core.event.NotifyEventType;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.NotifyMetadata;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotifyDirectiveSchemaTest {

    @Test
    @DisplayName("Successfully parse and transform @@notify on model")
    void testParseAndTransformNotifyDirective() {
        String schema = """
                model Student {
                    id   Int    @id @default(autoincrement())
                    name String
                    gpa  Float?

                    @@notify(on: [CREATE, UPDATE, DELETE])
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors())
                .withFailMessage("Expected no validation errors, but got: %s", validation.getErrors())
                .isFalse();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ModelMetadata student = registry.getModel("Student");

        assertThat(student.isNotifyEnabled()).isTrue();
        NotifyMetadata notify = student.notifyMetadata();
        assertThat(notify.listensTo(NotifyEventType.CREATE)).isTrue();
        assertThat(notify.listensTo(NotifyEventType.UPDATE)).isTrue();
        assertThat(notify.listensTo(NotifyEventType.DELETE)).isTrue();
    }

    @Test
    @DisplayName("Successfully parse partial events list e.g. [CREATE, UPDATE]")
    void testParsePartialEventsList() {
        String schema = """
                model Student {
                    id   Int    @id
                    name String

                    @@notify(on: [CREATE, UPDATE])
                }
                """;

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ModelMetadata student = registry.getModel("Student");

        assertThat(student.isNotifyEnabled()).isTrue();
        assertThat(student.notifyMetadata().listensTo(NotifyEventType.CREATE)).isTrue();
        assertThat(student.notifyMetadata().listensTo(NotifyEventType.UPDATE)).isTrue();
        assertThat(student.notifyMetadata().listensTo(NotifyEventType.DELETE)).isFalse();
    }

    @Test
    @DisplayName("Reject @@notify on read-only view model")
    void testRejectNotifyOnViewModel() {
        String schema = """
                model Student {
                    id Int @id
                    name String
                }

                view StudentView {
                    id Int @id
                    name String
                    @@from(Student, as: "s")
                    @@notify(on: [CREATE])
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isTrue();
        assertThat(validation.getErrors())
                .anyMatch(err -> err.message().contains("Read-only view 'StudentView' cannot declare @@notify directive"));
    }

    @Test
    @DisplayName("Reject @@notify with invalid event type")
    void testRejectNotifyWithInvalidEvent() {
        String schema = """
                model Student {
                    id Int @id
                    name String

                    @@notify(on: [CREATE, PURGE])
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isTrue();
        assertThat(validation.getErrors())
                .anyMatch(err -> err.message().contains("Invalid notify event type 'PURGE' on model 'Student'"));
    }

    @Test
    @DisplayName("Reject @@notify without 'on' argument")
    void testRejectNotifyWithoutOnArgument() {
        String schema = """
                model Student {
                    id Int @id
                    name String

                    @@notify
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isTrue();
        assertThat(validation.getErrors())
                .anyMatch(err -> err.message().contains("@@notify directive on model 'Student' must specify an 'on' events argument"));
    }
}
