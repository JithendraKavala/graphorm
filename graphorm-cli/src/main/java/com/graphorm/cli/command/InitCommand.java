package com.graphorm.cli.command;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * Initializes a new GraphORM starter project with sample schema and configuration.
 */
@Command(
        name = "init",
        description = "Initialize a new GraphORM project with starter schema and configuration",
        mixinStandardHelpOptions = true
)
public class InitCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Option(
            names = {"-d", "--dir"},
            description = "Target directory for project initialization (default: ${DEFAULT-VALUE})",
            defaultValue = "."
    )
    private Path targetDir;

    @Option(
            names = {"-p", "--package"},
            description = "Base Java package for code generation (default: ${DEFAULT-VALUE})",
            defaultValue = "com.example.graphorm"
    )
    private String basePackage;

    @Option(
            names = {"--dialect"},
            description = "Database dialect (default: ${DEFAULT-VALUE})",
            defaultValue = "postgresql"
    )
    private String dialect;

    @Option(
            names = {"--force"},
            description = "Overwrite existing files if present (default: ${DEFAULT-VALUE})",
            defaultValue = "false"
    )
    private boolean force;

    public InitCommand() {}

    public InitCommand(Path targetDir, String basePackage, String dialect, boolean force) {
        this.targetDir = targetDir;
        this.basePackage = basePackage;
        this.dialect = dialect;
        this.force = force;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec != null ? spec.commandLine().getOut() : new PrintWriter(System.out, true);
        PrintWriter err = spec != null ? spec.commandLine().getErr() : new PrintWriter(System.err, true);

        if (targetDir == null) {
            targetDir = Path.of(".");
        }

        try {
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
        } catch (IOException e) {
            err.println("Error creating target directory " + targetDir + ": " + e.getMessage());
            return 1;
        }

        Path schemaFile = targetDir.resolve("schema.graphorm");
        Path propertiesFile = targetDir.resolve("graphorm.properties");

        if (!force) {
            if (Files.exists(schemaFile)) {
                err.println("Error: File already exists: " + schemaFile + ". Use --force to overwrite.");
                return 1;
            }
            if (Files.exists(propertiesFile)) {
                err.println("Error: File already exists: " + propertiesFile + ". Use --force to overwrite.");
                return 1;
            }
        }

        String starterSchema = generateStarterSchema(basePackage, dialect);
        String starterProperties = generateStarterProperties(dialect);

        try {
            Files.writeString(schemaFile, starterSchema, StandardCharsets.UTF_8);
            Files.writeString(propertiesFile, starterProperties, StandardCharsets.UTF_8);

            out.println("Initialized GraphORM starter project in '" + targetDir + "'!");
            out.println("Created files:");
            out.println("  - " + schemaFile);
            out.println("  - " + propertiesFile);
            out.println();
            out.println("Next steps:");
            out.println("  1. Customize " + schemaFile + " to define your models and views.");
            out.println("  2. Run 'graphorm validate -s " + schemaFile + "' to verify schema correctness.");
            out.println("  3. Run 'graphorm generate -s " + schemaFile + "' to generate Java models, views, and client.");

            return 0;
        } catch (IOException e) {
            err.println("Failed to write starter files: " + e.getMessage());
            return 1;
        }
    }

    private String generateStarterSchema(String pkg, String dbDialect) {
        return """
                // GraphORM Schema Definition
                // Learn more: https://github.com/graphorm/graphorm

                datasource db {
                  provider = "%s"
                  url      = env("DATABASE_URL")
                }

                generator client {
                  provider = "graphorm-client-java"
                  package  = "%s"
                }

                enum Role {
                  USER
                  ADMIN
                }

                model User {
                  id        Int      @id @default(autoincrement())
                  email     String   @unique
                  name      String
                  role      Role     @default(USER)
                  createdAt DateTime @default(now())
                  posts     Post[]
                }

                model Post {
                  id        Int      @id @default(autoincrement())
                  title     String
                  content   String?
                  published Boolean  @default(false)
                  authorId  Int
                  author    User     @relation(fields: [authorId], references: [id])
                }

                view UserSummary {
                  userId    Int    @id
                  userName  String
                  postCount Int

                  @@view
                  @@map("user_summaries")
                }
                """.formatted(dbDialect, pkg);
    }

    private String generateStarterProperties(String dbDialect) {
        return """
                # GraphORM Configuration Properties
                graphorm.schema-path=schema.graphorm
                graphorm.dialect=%s
                graphorm.sql-logging=true
                graphorm.batch-size=100
                """.formatted(dbDialect);
    }
}
