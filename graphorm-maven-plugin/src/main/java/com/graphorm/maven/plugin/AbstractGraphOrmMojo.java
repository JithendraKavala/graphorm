package com.graphorm.maven.plugin;

import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.schema.ast.GeneratorNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.StringLiteralNode;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.validation.Diagnostic;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Base Mojo providing shared operations for GraphORM Maven goals.
 */
public abstract class AbstractGraphOrmMojo extends AbstractMojo {

    public static final String DEFAULT_PACKAGE = "com.graphorm.generated";

    /**
     * Parses and validates the schema file, returning the AST document.
     *
     * @param schemaFile the schema file
     * @return validated SchemaDocument
     * @throws MojoExecutionException if reading, parsing, or validation fails
     */
    protected SchemaDocument parseAndValidateSchema(File schemaFile) throws MojoExecutionException {
        if (!schemaFile.exists() || !schemaFile.isFile()) {
            throw new MojoExecutionException("GraphORM schema file does not exist: " + schemaFile.getAbsolutePath());
        }

        String source;
        try {
            source = Files.readString(schemaFile.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read schema file: " + schemaFile.getAbsolutePath(), e);
        }

        String fileName = schemaFile.getName();
        GraphOrmParserFacade.ParseResult parseResult = GraphOrmParserFacade.parseWithDiagnostics(source, fileName);

        if (parseResult.hasErrors()) {
            StringBuilder sb = new StringBuilder();
            sb.append("GraphORM schema has syntax error(s) in ").append(fileName).append(":\n");
            for (Diagnostic d : parseResult.diagnostics()) {
                sb.append(d.formatWithCaret(source));
            }
            throw new MojoExecutionException(sb.toString());
        }

        SchemaDocument document = parseResult.document();
        ValidationResult validationResult = SchemaValidator.validate(document);

        if (validationResult.hasErrors()) {
            StringBuilder sb = new StringBuilder();
            sb.append("GraphORM schema has semantic validation error(s) in ").append(fileName).append(":\n");
            for (Diagnostic d : validationResult.getErrors()) {
                sb.append(d.formatWithCaret(source));
            }
            throw new MojoExecutionException(sb.toString());
        }

        return document;
    }

    /**
     * Resolves the effective Java package from CLI/plugin parameters, schema generator blocks, or project defaults.
     */
    protected String resolveBasePackage(SchemaDocument document, String explicitPackage, String fallbackPackage)
            throws MojoExecutionException {
        if (explicitPackage != null && !explicitPackage.isBlank()) {
            validatePackageName(explicitPackage);
            return explicitPackage;
        }

        for (GeneratorNode gen : document.generators()) {
            if ("client".equalsIgnoreCase(gen.name())) {
                var pkgProp = gen.getProperty("package");
                if (pkgProp.isPresent() && pkgProp.get() instanceof StringLiteralNode str) {
                    validatePackageName(str.value());
                    return str.value();
                }
            }
        }

        for (GeneratorNode gen : document.generators()) {
            var pkgProp = gen.getProperty("package");
            if (pkgProp.isPresent() && pkgProp.get() instanceof StringLiteralNode str) {
                validatePackageName(str.value());
                return str.value();
            }
        }

        if (fallbackPackage != null && !fallbackPackage.isBlank()) {
            String sanitized = fallbackPackage + ".generated";
            if (GeneratedSourceFile.isValidPackageName(sanitized)) {
                return sanitized;
            }
        }

        return DEFAULT_PACKAGE;
    }

    private void validatePackageName(String packageName) throws MojoExecutionException {
        if (!GeneratedSourceFile.isValidPackageName(packageName)) {
            throw new MojoExecutionException(
                    "Invalid package name identifier '" + packageName + "'. Package names must be valid Java identifiers."
            );
        }
    }

    /**
     * Recursively cleans a directory.
     */
    protected void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
