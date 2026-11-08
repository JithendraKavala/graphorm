package com.graphorm.maven.plugin;

import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.generator.engine.CodeGeneratorEngine;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.schema.ast.SchemaDocument;
import com.graphorm.schema.transformer.SchemaToMetadataTransformer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Maven Mojo to generate Java 21 model records, view models, input DTOs, filter criteria, and client accessors
 * from a GraphORM schema during the {@code generate-sources} phase.
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.GENERATE_SOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE,
        threadSafe = true
)
public class GenerateMojo extends AbstractGraphOrmMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "graphorm.schemaFile", defaultValue = "${project.basedir}/src/main/resources/schema.graphorm")
    private File schemaFile;

    @Parameter(property = "graphorm.outputDirectory", defaultValue = "${project.build.directory}/generated-sources/graphorm")
    private File outputDirectory;

    @Parameter(property = "graphorm.basePackage")
    private String basePackage;

    @Parameter(property = "graphorm.clean", defaultValue = "false")
    private boolean clean;

    @Parameter(property = "graphorm.addSource", defaultValue = "true")
    private boolean addSource;

    @Parameter(property = "graphorm.skip", defaultValue = "false")
    private boolean skip;

    public GenerateMojo() {}

    public GenerateMojo(MavenProject project, File schemaFile, File outputDirectory, String basePackage, boolean clean, boolean addSource) {
        this.project = project;
        this.schemaFile = schemaFile;
        this.outputDirectory = outputDirectory;
        this.basePackage = basePackage;
        this.clean = clean;
        this.addSource = addSource;
        this.skip = false;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setSchemaFile(File schemaFile) {
        this.schemaFile = schemaFile;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public void setAddSource(boolean addSource) {
        this.addSource = addSource;
    }

    public void setSkip(boolean skip) {
        this.skip = skip;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Skipping GraphORM code generation (graphorm.skip=true)");
            return;
        }

        File effectiveSchemaFile = schemaFile;
        if (!effectiveSchemaFile.exists()) {
            // Check fallback: ${project.basedir}/schema.graphorm
            if (project != null && project.getBasedir() != null) {
                File rootFallback = new File(project.getBasedir(), "schema.graphorm");
                if (rootFallback.exists()) {
                    effectiveSchemaFile = rootFallback;
                }
            }
        }

        if (!effectiveSchemaFile.exists()) {
            getLog().info("GraphORM schema file not found at '" + schemaFile.getPath() + "'. Skipping code generation.");
            return;
        }

        getLog().info("Generating GraphORM Java sources from schema: " + effectiveSchemaFile.getAbsolutePath());

        SchemaDocument document = parseAndValidateSchema(effectiveSchemaFile);
        String groupId = project != null ? project.getGroupId() : null;
        String effectivePackage = resolveBasePackage(document, basePackage, groupId);

        Path outPath = outputDirectory.toPath().toAbsolutePath().normalize();

        if (clean && Files.exists(outPath)) {
            try {
                cleanDirectory(outPath);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to clean output directory: " + outPath, e);
            }
        }

        try {
            ModelRegistry registry = SchemaToMetadataTransformer.transform(document);
            GeneratorConfig config = GeneratorConfig.of(effectivePackage);
            CodeGeneratorEngine engine = CodeGeneratorEngine.create(config);

            long startTime = System.currentTimeMillis();
            List<Path> writtenFiles = engine.writeAll(registry, outPath);
            long elapsed = System.currentTimeMillis() - startTime;

            getLog().info(String.format("Generated %d Java source file(s) into '%s' in %d ms (package: %s)",
                    writtenFiles.size(), outPath, elapsed, effectivePackage));

            if (addSource && project != null) {
                project.addCompileSourceRoot(outPath.toString());
                getLog().debug("Added compile source root: " + outPath);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate GraphORM sources: " + e.getMessage(), e);
        }
    }
}
