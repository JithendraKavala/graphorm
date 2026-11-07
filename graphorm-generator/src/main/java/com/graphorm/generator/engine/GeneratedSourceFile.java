package com.graphorm.generator.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

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

    private static final Pattern PACKAGE_PATTERN =
            Pattern.compile("^([a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*)?$");
    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*$");

    public GeneratedSourceFile {
        Objects.requireNonNull(packageName, "Package name must not be null");
        Objects.requireNonNull(className, "Class name must not be null");
        Objects.requireNonNull(content, "Content must not be null");

        if (!isValidPackageName(packageName)) {
            throw new IllegalArgumentException("Invalid Java package name: '" + packageName + "'");
        }
        if (!isValidClassName(className)) {
            throw new IllegalArgumentException("Invalid Java class name: '" + className + "'");
        }
    }

    public static boolean isValidPackageName(String packageName) {
        return packageName != null && PACKAGE_PATTERN.matcher(packageName).matches();
    }

    public static boolean isValidClassName(String className) {
        return className != null && CLASS_NAME_PATTERN.matcher(className).matches();
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
     * Enforces path containment to prevent path traversal vulnerabilities (SEC-03).
     *
     * @param rootDir root source output directory (e.g. "target/generated-sources/graphorm")
     * @return Path of the written file
     * @throws IOException if writing fails
     */
    public Path writeTo(Path rootDir) throws IOException {
        Objects.requireNonNull(rootDir, "rootDir must not be null");
        Path normalizedRootDir = rootDir.toAbsolutePath().normalize();
        Path targetFile = normalizedRootDir.resolve(relativePath()).normalize();

        if (!targetFile.startsWith(normalizedRootDir)) {
            throw new SecurityException("Path traversal attempt detected: target path '" + targetFile +
                    "' is outside root directory '" + normalizedRootDir + "'");
        }

        if (targetFile.getParent() != null) {
            Files.createDirectories(targetFile.getParent());
        }
        Files.writeString(targetFile, content, StandardCharsets.UTF_8);
        return targetFile;
    }
}
