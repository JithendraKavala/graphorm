package com.graphorm.generator.model;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.generator.util.JavaTypeResolver;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.List;
import java.util.Objects;

/**
 * Generates read-only Java 21 projection records matching schema view models.
 * <p>
 * STRICT READ-ONLY INVARIANT:
 * View model records represent computed database/virtual projections and have
 * <b>zero</b> mutation methods, <b>zero</b> setters, and <b>zero</b> with-methods.
 */
public final class ViewModelRecordGenerator {

    private ViewModelRecordGenerator() {}

    /**
     * Generates a read-only Java 21 record source file for the given {@link ViewModelMetadata}.
     *
     * @param viewModel the view model metadata
     * @param registry  the model registry
     * @param config    the generator configuration
     * @return the generated source file
     */
    public static GeneratedSourceFile generate(ViewModelMetadata viewModel, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(viewModel, "ViewModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.modelPackage();
        String viewName = viewModel.name();

        List<FieldMetadata> fields = viewModel.fields();

        // 1. Static Metadata Constant
        FieldSpec viewNameField = FieldSpec.builder(String.class, "VIEW_NAME", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", viewName)
                .build();

        FieldSpec isDatabaseBackedField = FieldSpec.builder(boolean.class, "IS_DATABASE_BACKED", Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$L", viewModel.isDatabaseBacked())
                .build();

        // 2. ClassSpec Builder (zero setters, zero mutation methods)
        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(viewName)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Read-only projection record representing the {@code $L} view.\n" +
                        "<p>\n" +
                        "STRICT READ-ONLY INVARIANT: View models are strictly immutable projections\n" +
                        "with zero mutation or setter methods.\n", viewName)
                .addField(viewNameField)
                .addField(isDatabaseBackedField);

        // Generate Java code via JavaPoet
        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        String rawCode = javaFile.toString();

        // Convert "public class ViewName {" into record signature
        StringBuilder recordHeader = new StringBuilder();
        recordHeader.append("public record ").append(viewName).append("(\n");
        for (int i = 0; i < fields.size(); i++) {
            FieldMetadata f = fields.get(i);
            TypeName ft = JavaTypeResolver.resolveFieldType(f, registry, packageName);
            recordHeader.append("        ").append(ft.toString()).append(" ").append(f.name());
            if (i < fields.size() - 1) {
                recordHeader.append(",");
            }
            recordHeader.append("\n");
        }
        recordHeader.append(") {");

        String recordCode = rawCode.replaceFirst("public class " + viewName + " \\{", recordHeader.toString());

        return new GeneratedSourceFile(packageName, viewName, recordCode);
    }
}
