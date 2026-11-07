package com.graphorm.generator.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("[SEC-03] GeneratedSourceFile Path Traversal & Identifier Validation Tests")
class GeneratedSourceFileTest {

    @Test
    @DisplayName("Should reject invalid package names containing path traversal sequences or illegal characters")
    void shouldRejectInvalidPackageNames() {
        assertThatThrownBy(() -> new GeneratedSourceFile("../../evil", "MyClass", "class MyClass {}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Java package name");

        assertThatThrownBy(() -> new GeneratedSourceFile("com.foo/bar", "MyClass", "class MyClass {}"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new GeneratedSourceFile("com..foo", "MyClass", "class MyClass {}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should reject invalid class names")
    void shouldRejectInvalidClassNames() {
        assertThatThrownBy(() -> new GeneratedSourceFile("com.example", "../EvilClass", "class EvilClass {}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Java class name");

        assertThatThrownBy(() -> new GeneratedSourceFile("com.example", "Class-With-Dashes", "class Test {}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should accept valid package and class names")
    void shouldAcceptValidPackageAndClassName() {
        GeneratedSourceFile file = new GeneratedSourceFile("com.graphorm.test", "UserEntity", "public record UserEntity() {}");
        assertThat(file.packageName()).isEqualTo("com.graphorm.test");
        assertThat(file.className()).isEqualTo("UserEntity");
        assertThat(file.fullyQualifiedName()).isEqualTo("com.graphorm.test.UserEntity");
        assertThat(file.relativePath()).isEqualTo(Path.of("com/graphorm/test/UserEntity.java"));
    }

    @Test
    @DisplayName("writeTo should safely write inside target rootDir")
    void shouldWriteToSafeTargetDirectory(@TempDir Path tempDir) throws IOException {
        GeneratedSourceFile file = new GeneratedSourceFile("com.graphorm.test", "UserEntity", "public record UserEntity() {}");
        Path written = file.writeTo(tempDir);

        assertThat(written).exists();
        assertThat(written.startsWith(tempDir)).isTrue();
    }
}
