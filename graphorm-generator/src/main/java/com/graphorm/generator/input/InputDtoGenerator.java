package com.graphorm.generator.input;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.generator.util.JavaTypeResolver;
import com.squareup.javapoet.*;

import javax.lang.model.element.Modifier;
import java.util.*;

/**
 * Generates CreateInput and UpdateInput DTOs strictly for regular models.
 * <p>
 * STRICT READ-ONLY INVARIANT:
 * View models must never have CreateInput or UpdateInput DTOs. Any attempt to
 * generate an input DTO for a view model strictly throws {@link ReadOnlyViewException}
 * or {@link IllegalArgumentException}.
 */
public final class InputDtoGenerator {

    private InputDtoGenerator() {}

    /**
     * Rejects any attempt to generate CreateInput for a view model.
     *
     * @throws ReadOnlyViewException always
     */
    public static GeneratedSourceFile generateCreateInput(ViewModelMetadata viewModel, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(viewModel, "ViewModelMetadata must not be null");
        throw new ReadOnlyViewException(viewModel.name(), "CREATE_INPUT",
                "Cannot generate CreateInput for read-only view model: " + viewModel.name());
    }

    /**
     * Rejects any attempt to generate UpdateInput for a view model.
     *
     * @throws ReadOnlyViewException always
     */
    public static GeneratedSourceFile generateUpdateInput(ViewModelMetadata viewModel, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(viewModel, "ViewModelMetadata must not be null");
        throw new ReadOnlyViewException(viewModel.name(), "UPDATE_INPUT",
                "Cannot generate UpdateInput for read-only view model: " + viewModel.name());
    }

    /**
     * Generates a CreateInput record for a regular entity model.
     *
     * @param model    the model metadata
     * @param registry the model registry
     * @param config   the generator configuration
     * @return the generated CreateInput source file
     */
    public static GeneratedSourceFile generateCreateInput(ModelMetadata model, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(model, "ModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.inputPackage();
        String className = model.name() + "CreateInput";
        ClassName inputClassName = ClassName.get(packageName, className);

        // Persistent fields excluding autoincrement/generated fields
        List<FieldMetadata> createFields = model.fields().stream()
                .filter(f -> !f.isRelation())
                .filter(f -> !f.isGenerated())
                .filter(f -> !f.isInjected())
                .toList();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Create input payload for {@link $L}.\n", model.name());

        // Static builder()
        ClassName builderClassName = inputClassName.nestedClass("Builder");
        classBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return new $T()", builderClassName)
                .build());

        // toMap() method
        MethodSpec.Builder toMapMethod = MethodSpec.methodBuilder("toMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Map.class, String.class, Object.class))
                .addStatement("$T<String, Object> map = new $T<>()", Map.class, LinkedHashMap.class);

        for (FieldMetadata field : createFields) {
            String colName = field.dbColumnName() != null && !field.dbColumnName().isBlank() ? field.dbColumnName() : field.name();
            toMapMethod.addStatement("if (this.$L != null) map.put($S, this.$L)", field.name(), colName, field.name());
        }
        toMapMethod.addStatement("return $T.unmodifiableMap(map)", Collections.class);
        classBuilder.addMethod(toMapMethod.build());

        // Nested Builder
        TypeSpec.Builder builderClass = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        for (FieldMetadata field : createFields) {
            TypeName ft = JavaTypeResolver.resolveFieldType(field, registry, config.modelPackage());
            String fn = field.name();

            builderClass.addField(FieldSpec.builder(ft, fn, Modifier.PRIVATE).build());
            builderClass.addMethod(MethodSpec.methodBuilder(fn)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get("", "Builder"))
                    .addParameter(ft, fn)
                    .addStatement("this.$L = $L", fn, fn)
                    .addStatement("return this")
                    .build());
        }

        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(inputClassName);

        StringBuilder buildCall = new StringBuilder("return new $T(");
        List<Object> buildArgs = new ArrayList<>();
        buildArgs.add(inputClassName);
        for (int i = 0; i < createFields.size(); i++) {
            if (i > 0) buildCall.append(", ");
            buildCall.append(createFields.get(i).name());
        }
        buildCall.append(")");
        buildMethod.addStatement(buildCall.toString(), buildArgs.toArray());
        builderClass.addMethod(buildMethod.build());

        classBuilder.addType(builderClass.build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        String rawCode = javaFile.toString();

        StringBuilder recordHeader = new StringBuilder();
        recordHeader.append("public record ").append(className).append("(\n");
        for (int i = 0; i < createFields.size(); i++) {
            FieldMetadata f = createFields.get(i);
            TypeName ft = JavaTypeResolver.resolveFieldType(f, registry, config.modelPackage());
            recordHeader.append("        ").append(ft.toString()).append(" ").append(f.name());
            if (i < createFields.size() - 1) {
                recordHeader.append(",");
            }
            recordHeader.append("\n");
        }
        recordHeader.append(") {");

        String recordCode = rawCode.replaceFirst("public class " + className + " \\{", recordHeader.toString());

        return new GeneratedSourceFile(packageName, className, recordCode);
    }

    /**
     * Generates an UpdateInput record for a regular entity model.
     *
     * @param model    the model metadata
     * @param registry the model registry
     * @param config   the generator configuration
     * @return the generated UpdateInput source file
     */
    public static GeneratedSourceFile generateUpdateInput(ModelMetadata model, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(model, "ModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.inputPackage();
        String className = model.name() + "UpdateInput";
        ClassName inputClassName = ClassName.get(packageName, className);

        // Persistent fields excluding primary key fields
        Set<String> pkNames = new HashSet<>(model.primaryKeyFields());
        List<FieldMetadata> updateFields = model.fields().stream()
                .filter(f -> !f.isRelation())
                .filter(f -> !pkNames.contains(f.name()))
                .filter(f -> !f.isPrimaryKey())
                .filter(f -> !f.isInjected())
                .toList();

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Update input payload for {@link $L}.\n", model.name());

        // Static builder()
        ClassName builderClassName = inputClassName.nestedClass("Builder");
        classBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return new $T()", builderClassName)
                .build());

        // toMap() method
        MethodSpec.Builder toMapMethod = MethodSpec.methodBuilder("toMap")
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Map.class, String.class, Object.class))
                .addStatement("$T<String, Object> map = new $T<>()", Map.class, LinkedHashMap.class);

        for (FieldMetadata field : updateFields) {
            String colName = field.dbColumnName() != null && !field.dbColumnName().isBlank() ? field.dbColumnName() : field.name();
            toMapMethod.addStatement("if (this.$L != null) map.put($S, this.$L)", field.name(), colName, field.name());
        }
        toMapMethod.addStatement("return $T.unmodifiableMap(map)", Collections.class);
        classBuilder.addMethod(toMapMethod.build());

        // Nested Builder
        TypeSpec.Builder builderClass = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);

        for (FieldMetadata field : updateFields) {
            TypeName ft = JavaTypeResolver.resolveFieldType(field, registry, config.modelPackage());
            String fn = field.name();

            builderClass.addField(FieldSpec.builder(ft, fn, Modifier.PRIVATE).build());
            builderClass.addMethod(MethodSpec.methodBuilder(fn)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ClassName.get("", "Builder"))
                    .addParameter(ft, fn)
                    .addStatement("this.$L = $L", fn, fn)
                    .addStatement("return this")
                    .build());
        }

        MethodSpec.Builder buildMethod = MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(inputClassName);

        StringBuilder buildCall = new StringBuilder("return new $T(");
        List<Object> buildArgs = new ArrayList<>();
        buildArgs.add(inputClassName);
        for (int i = 0; i < updateFields.size(); i++) {
            if (i > 0) buildCall.append(", ");
            buildCall.append(updateFields.get(i).name());
        }
        buildCall.append(")");
        buildMethod.addStatement(buildCall.toString(), buildArgs.toArray());
        builderClass.addMethod(buildMethod.build());

        classBuilder.addType(builderClass.build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        String rawCode = javaFile.toString();

        StringBuilder recordHeader = new StringBuilder();
        recordHeader.append("public record ").append(className).append("(\n");
        for (int i = 0; i < updateFields.size(); i++) {
            FieldMetadata f = updateFields.get(i);
            TypeName ft = JavaTypeResolver.resolveFieldType(f, registry, config.modelPackage());
            recordHeader.append("        ").append(ft.toString()).append(" ").append(f.name());
            if (i < updateFields.size() - 1) {
                recordHeader.append(",");
            }
            recordHeader.append("\n");
        }
        recordHeader.append(") {");

        String recordCode = rawCode.replaceFirst("public class " + className + " \\{", recordHeader.toString());

        return new GeneratedSourceFile(packageName, className, recordCode);
    }
}
