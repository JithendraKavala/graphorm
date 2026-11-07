package com.graphorm.cli.command;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.generator.engine.CodeGeneratorEngine;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.schema.ast.ExpressionNode;
import com.graphorm.schema.ast.GeneratorNode;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.ast.StringLiteralNode;
import com.graphorm.schema.parser.GraphOrmParserFacade;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import com.graphorm.schema.validation.Diagnostic;
import com.graphorm.schema.validation.SchemaValidator;
import com.graphorm.schema.validation.ValidationResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Generates Java source code (models, views, inputs, filters, metadata, client API) from a GraphORM schema.
 */
@Command(
        name = "generate",
        description = "Generate Java models, views, input DTOs, filter criteria, and client API from schema",
        mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    private static final String DEFAULT_PACKAGE = "com.graphorm.generated";

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-s", "--schema"},
            description = "Path to the GraphORM schema file (default: ${DEFAULT-VALUE})",
            defaultValue = "schema.graphorm"
    )
    private Path schemaPath;

    @Option(
            names = {"-o", "--output"},
            description = "Target directory for generated Java sources (default: ${DEFAULT-VALUE})",
            defaultValue = "target/generated-sources/graphorm"
    )
    private Path outputDir;

    @Option(
            names = {"-p", "--package"},
            description = "Base Java package for generated classes (overrides generator block in schema)"
    )
    private String basePackage;

    @Option(
            names = {"--clean"},
            description = "Clean output directory before generation (default: ${DEFAULT-VALUE})",
            defaultValue = "false"
    )
    private boolean clean;

    public GenerateCommand() {}

    public GenerateCommand(Path schemaPath, Path outputDir, String basePackage, boolean clean) {
        this.schemaPath = schemaPath;
        this.outputDir = outputDir;
        this.basePackage = basePackage;
        this.clean = clean;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        if (schemaPath == null || !Files.exists(schemaPath)) {
            err.println("Error: Schema file not found: " + schemaPath);
            return 1;
        }

        if (Files.isDirectory(schemaPath)) {
            err.println("Error: Schema path is a directory, not a file: " + schemaPath);
            return 1;
        }

        String source;
        try {
            source = Files.readString(schemaPath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            err.println("Error reading schema file: " + e.getMessage());
            return 1;
        }

        String fileName = schemaPath.getFileName().toString();
        GraphOrmParserFacade.ParseResult parseResult = GraphOrmParserFacade.parseWithDiagnostics(source, fileName);

        if (parseResult.hasErrors()) {
            err.println("Cannot generate code: schema has syntax error(s):");
            for (Diagnostic d : parseResult.diagnostics()) {
                err.print(d.formatWithCaret(source));
            }
            return 1;
        }

        SchemaDocument document = parseResult.document();
        ValidationResult validationResult = SchemaValidator.validate(document);

        if (validationResult.hasErrors()) {
            err.println("Cannot generate code: schema has semantic error(s):");
            for (Diagnostic d : validationResult.getErrors()) {
                err.print(d.formatWithCaret(source));
            }
            return 1;
        }

        // Determine effective base package
        String effectivePackage = basePackage;
        if (effectivePackage == null || effectivePackage.isBlank()) {
            effectivePackage = resolvePackageFromSchema(document);
        }

        // Clean output directory if requested
        if (clean && Files.exists(outputDir)) {
            try {
                cleanDirectory(outputDir);
            } catch (IOException e) {
                err.println("Error cleaning output directory " + outputDir + ": " + e.getMessage());
                return 1;
            }
        }

        try {
            long startTime = System.currentTimeMillis();

            ModelRegistry registry = SchemaToMetadataTransformer.transform(document);
            GeneratorConfig config = GeneratorConfig.of(effectivePackage);
            CodeGeneratorEngine engine = CodeGeneratorEngine.create(config);

            List<Path> writtenFiles = engine.writeAll(registry, outputDir);
            long elapsed = System.currentTimeMillis() - startTime;

            out.printf("Successfully generated %d Java source file(s) into '%s' (%d ms)%n",
                    writtenFiles.size(), outputDir, elapsed);
            out.println("  Base package: " + effectivePackage);
            out.printf("  Entities: %d model(s), %d view(s), %d composite type(s), %d enum(s)%n",
                    registry.getAllModels().size(),
                    registry.getAllViewModels().size(),
                    registry.getAllCompositeTypes().size(),
                    registry.getAllEnums().size()
            );

            return 0;
        } catch (Exception e) {
            err.println("Code generation failed: " + e.getMessage());
            return 1;
        }
    }

    private String resolvePackageFromSchema(SchemaDocument document) {
        for (GeneratorNode gen : document.generators()) {
            if ("client".equalsIgnoreCase(gen.name())) {
                var pkgProp = gen.getProperty("package");
                if (pkgProp.isPresent() && pkgProp.get() instanceof StringLiteralNode str) {
                    return str.value();
                }
            }
        }
        for (GeneratorNode gen : document.generators()) {
            var pkgProp = gen.getProperty("package");
            if (pkgProp.isPresent() && pkgProp.get() instanceof StringLiteralNode str) {
                return str.value();
            }
        }
        return DEFAULT_PACKAGE;
    }

    private void cleanDirectory(Path directory) throws IOException {
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
                if (!dir.equals(directory)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
