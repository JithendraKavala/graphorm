package com.graphorm.maven.plugin;

import com.graphorm.schema.ast.SchemaDocument;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Maven Mojo to validate a GraphORM schema file during the {@code validate} phase without writing files.
 */
@Mojo(
        name = "validate",
        defaultPhase = LifecyclePhase.VALIDATE,
        threadSafe = true
)
public class ValidateMojo extends AbstractGraphOrmMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "graphorm.schemaFile", defaultValue = "${project.basedir}/src/main/resources/schema.graphorm")
    private File schemaFile;

    @Parameter(property = "graphorm.skipValidate", defaultValue = "false")
    private boolean skipValidate;

    public ValidateMojo() {}

    public ValidateMojo(MavenProject project, File schemaFile) {
        this.project = project;
        this.schemaFile = schemaFile;
        this.skipValidate = false;
    }

    public void setProject(MavenProject project) {
        this.project = project;
    }

    public void setSchemaFile(File schemaFile) {
        this.schemaFile = schemaFile;
    }

    public void setSkipValidate(boolean skipValidate) {
        this.skipValidate = skipValidate;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipValidate) {
            getLog().info("Skipping GraphORM schema validation (graphorm.skipValidate=true)");
            return;
        }

        File effectiveSchemaFile = schemaFile;
        if (!effectiveSchemaFile.exists() && project != null && project.getBasedir() != null) {
            File rootFallback = new File(project.getBasedir(), "schema.graphorm");
            if (rootFallback.exists()) {
                effectiveSchemaFile = rootFallback;
            }
        }

        if (!effectiveSchemaFile.exists()) {
            getLog().warn("GraphORM schema file not found at '" + schemaFile.getPath() + "'. Skipping schema validation.");
            return;
        }

        getLog().info("Validating GraphORM schema: " + effectiveSchemaFile.getAbsolutePath());
        SchemaDocument document = parseAndValidateSchema(effectiveSchemaFile);
        getLog().info(String.format("Schema is valid! Models: %d, Views: %d, Composite Types: %d, Enums: %d",
                document.models().size(),
                document.viewModels().size(),
                document.compositeTypes().size(),
                document.enums().size()));
    }
}
