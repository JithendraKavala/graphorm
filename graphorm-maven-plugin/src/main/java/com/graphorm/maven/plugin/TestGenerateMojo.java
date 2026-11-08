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
 * Maven Mojo to generate test Java 21 model records and client accessors from a test GraphORM schema
 * during the {@code generate-test-sources} phase.
 */
@Mojo(
        name = "test-generate",
        defaultPhase = LifecyclePhase.GENERATE_TEST_SOURCES,
        requiresDependencyResolution = ResolutionScope.TEST,
        threadSafe = true
)
public class TestGenerateMojo extends AbstractGraphOrmMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "graphorm.testSchemaFile", defaultValue = "${project.basedir}/src/test/resources/schema.graphorm")
    private File testSchemaFile;

    @Parameter(property = "graphorm.testOutputDirectory", defaultValue = "${project.build.directory}/generated-test-sources/graphorm")
    private File testOutputDirectory;

    @Parameter(property = "graphorm.testBasePackage")
    private String testBasePackage;

    @Parameter(property = "graphorm.clean", defaultValue = "false")
    private boolean clean;

    @Parameter(property = "graphorm.addTestSources", defaultValue = "true")
    private boolean addTestSources;

    @Parameter(property = "graphorm.skipTestGenerate", defaultValue = "false")
    private boolean skipTestGenerate;

    public TestGenerateMojo() {}

    public TestGenerateMojo(MavenProject project, File testSchemaFile, File testOutputDirectory, String testBasePackage, boolean clean, boolean addTestSources) {
        this.project = project;
        this.testSchemaFile = testSchemaFile;
        this.testOutputDirectory = testOutputDirectory;
        this.testBasePackage = testBasePackage;
        this.clean = clean;
        this.addTestSources = addTestSources;
        this.skipTestGenerate = false;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setTestSchemaFile(File testSchemaFile) {
        this.testSchemaFile = testSchemaFile;
    }

    public void setTestOutputDirectory(File testOutputDirectory) {
        this.testOutputDirectory = testOutputDirectory;
    }

    public void setTestBasePackage(String testBasePackage) {
        this.testBasePackage = testBasePackage;
    }

    public void setClean(boolean clean) {
        this.clean = clean;
    }

    public void setAddTestSources(boolean addTestSources) {
        this.addTestSources = addTestSources;
    }

    public void setSkipTestGenerate(boolean skipTestGenerate) {
        this.skipTestGenerate = skipTestGenerate;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTestGenerate) {
            getLog().info("Skipping GraphORM test code generation (graphorm.skipTestGenerate=true)");
            return;
        }

        if (testSchemaFile == null || !testSchemaFile.exists()) {
            getLog().info("GraphORM test schema file not found at '" + (testSchemaFile != null ? testSchemaFile.getPath() : "null") + "'. Skipping test code generation.");
            return;
        }

        getLog().info("Generating GraphORM test Java sources from schema: " + testSchemaFile.getAbsolutePath());

        SchemaDocument document = parseAndValidateSchema(testSchemaFile);
        String groupId = project != null ? project.getGroupId() : null;
        String effectivePackage = resolveBasePackage(document, testBasePackage, groupId);

        Path outPath = testOutputDirectory.toPath().toAbsolutePath().normalize();

        if (clean && Files.exists(outPath)) {
            try {
                cleanDirectory(outPath);
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to clean test output directory: " + outPath, e);
            }
        }

        try {
            ModelRegistry registry = SchemaToMetadataTransformer.transform(document);
            GeneratorConfig config = GeneratorConfig.of(effectivePackage);
            CodeGeneratorEngine engine = CodeGeneratorEngine.create(config);

            long startTime = System.currentTimeMillis();
            List<Path> writtenFiles = engine.writeAll(registry, outPath);
            long elapsed = System.currentTimeMillis() - startTime;

            getLog().info(String.format("Generated %d test Java source file(s) into '%s' in %d ms (package: %s)",
                    writtenFiles.size(), outPath, elapsed, effectivePackage));

            if (addTestSources && project != null) {
                project.addTestCompileSourceRoot(outPath.toString());
                getLog().debug("Added test compile source root: " + outPath);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed to generate GraphORM test sources: " + e.getMessage(), e);
        }
    }
}
