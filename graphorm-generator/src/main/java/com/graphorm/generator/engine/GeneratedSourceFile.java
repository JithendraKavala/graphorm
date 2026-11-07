package com.graphorm.generator.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Represents a single generated Java source file with its package, class name, and text content.
 *
 * @param packageName target Java package name
 * @param className   simple class/record name
 * @param content     full Java source code content
 */
public record GeneratedSourceFile(
        String packageName,
        String className,
        String content
) {

    public GeneratedSourceFile {
        Objects.requireNonNull(packageName, "Package name must not be null");
        Objects.requireNonNull(className, "Class name must not be null");
        Objects.requireNonNull(content, "Content must not be null");
    }

    /**
     * Fully-qualified name of the generated type.
     */
    public String fullyQualifiedName() {
        return packageName.isEmpty() ? className : packageName + "." + className;
    }

    /**
     * Relative file path of the source file (e.g. "com/example/Student.java").
     */
    public Path relativePath() {
        String packagePath = packageName.replace('.', '/');
        return packagePath.isEmpty() ? Path.of(className + ".java") : Path.of(packagePath, className + ".java");
    }

    /**
     * Writes the source code to the target root directory.
     *
     * @param rootDir root source output directory (e.g. "target/generated-sources/graphorm")
     * @return Path of the written file
     * @throws IOException if writing fails
     */
    public Path writeTo(Path rootDir) throws IOException {
        Path targetFile = rootDir.resolve(relativePath());
        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Files.writeString(targetFile, content, StandardCharsets.UTF_8);
        return targetFile;
    }
}
