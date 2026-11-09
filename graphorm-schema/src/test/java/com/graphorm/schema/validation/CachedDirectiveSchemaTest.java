package com.graphorm.schema.validation;

import com.graphorm.core.metadata.CacheMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CachedDirectiveSchemaTest {

    @Test
    @DisplayName("Successfully parse and transform @cached on model and view")
    void testParseAndTransformCachedDirective() {
        String schema = """
                model Student @cached(ttl: "300s", tags: ["Student"]) {
                    id Int @id
                    name String
                }

                model Enrollment {
                    id Int @id
                    studentId Int
                    courseName String
                }

                view StudentDashboard @cached(ttl: "60s", tags: ["Student", "Enrollment"]) {
                    id Int @id
                    studentName String
                    @@from(Student, as: "s")
                    @@leftJoin(Enrollment, as: "e", on: "s.id = e.studentId")
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors())
                .withFailMessage("Expected no validation errors, but got: %s", validation.getErrors())
                .isFalse();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);

        ModelMetadata student = registry.getModel("Student");
        assertThat(student.isCached()).isTrue();
        assertThat(student.cacheTtl()).isEqualTo(Duration.ofSeconds(300));
        assertThat(student.cacheTags()).containsExactly("Student");

        ViewModelMetadata dashboard = registry.getViewModel("StudentDashboard");
        assertThat(dashboard.isCached()).isTrue();
        assertThat(dashboard.cacheTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(dashboard.cacheTags()).containsExactlyInAnyOrder("Student", "Enrollment");
    }

    @Test
    @DisplayName("Reject @cached with invalid duration syntax")
    void testInvalidDurationSyntax() {
        String schema = """
                model Student @cached(ttl: "invalid_time", tags: ["Student"]) {
                    id Int @id
                    name String
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isTrue();
        assertThat(validation.getErrors().getFirst().message())
                .contains("Invalid duration syntax for cache ttl on 'Student'");
    }

    @Test
    @DisplayName("Reject @cached without ttl argument")
    void testMissingTtlArgument() {
        String schema = """
                model Student @cached {
                    id Int @id
                    name String
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isTrue();
        assertThat(validation.getErrors().getFirst().message())
                .contains("Cached directive on 'Student' must specify a 'ttl' duration argument");
    }

    @Test
    @DisplayName("Reject @cached with tags referencing non-existent entity")
    void testNonExistentTagEntity() {
        String schema = """
                model Student @cached(ttl: "60s", tags: ["NonExistentEntity"]) {
                    id Int @id
                    name String
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isTrue();
        assertThat(validation.getErrors().getFirst().message())
                .contains("Cache tag 'NonExistentEntity' on 'Student' does not reference a valid entity");
    }

    @Test
    @DisplayName("Block-level @@cached inside view model is also supported")
    void testBlockLevelCachedInsideView() {
        String schema = """
                model Student {
                    id Int @id
                    name String
                }

                view StudentSummary {
                    id Int @id
                    studentName String
                    @@from(Student, as: "s")
                    @@cached(ttl: "15m", tags: ["Student"])
                }
                """;

        var doc = GraphOrmParserFacade.parse(schema);
        ValidationResult validation = SchemaValidator.validate(doc);
        assertThat(validation.hasErrors()).isFalse();

        ModelRegistry registry = SchemaToMetadataTransformer.transform(schema);
        ViewModelMetadata vm = registry.getViewModel("StudentSummary");
        assertThat(vm.isCached()).isTrue();
        assertThat(vm.cacheTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(vm.cacheTags()).containsExactly("Student");
    }
}
