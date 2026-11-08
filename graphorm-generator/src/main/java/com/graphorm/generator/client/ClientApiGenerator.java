package com.graphorm.generator.client;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.DeleteQuery;
import com.graphorm.core.query.InsertQuery;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.UpdateQuery;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.generator.engine.GeneratedSourceFile;
import com.graphorm.generator.engine.GeneratorConfig;
import com.graphorm.jdbc.builder.SelectBuilder;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.executor.JdbcExecutor;
import com.graphorm.jdbc.view.ViewQueryPlanner;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import javax.lang.model.element.Modifier;
import javax.sql.DataSource;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Generates type-safe Prisma-style client accessors for models and views,
 * as well as the root {@code GraphOrmClient}.
 * <p>
 * Enforces strict compile-time read-only segregation:
 * View model client accessors implement {@link ReadOnlyModelAccessor} and have
 * zero mutation methods, while regular model accessors implement {@link ModelAccessor}.
 */
public final class ClientApiGenerator {

    private ClientApiGenerator() {}

    /**
     * Generates the client accessor for a regular model.
     */
    public static GeneratedSourceFile generateModelClient(ModelMetadata model, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(model, "ModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.clientPackage();
        String modelName = model.name();
        String clientClassName = modelName + "Client";

        ClassName modelType = ClassName.get(config.modelPackage(), modelName);
        ClassName filterType = ClassName.get(config.filterPackage(), modelName + "Filter");
        ClassName createInputType = ClassName.get(config.inputPackage(), modelName + "CreateInput");
        ClassName updateInputType = ClassName.get(config.inputPackage(), modelName + "UpdateInput");

        TypeName accessorInterface = ParameterizedTypeName.get(
                ClassName.get("com.graphorm.generator.client", "ModelAccessor"),
                modelType,
                filterType,
                createInputType,
                updateInputType
        );

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(clientClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(accessorInterface)
                .addJavadoc("Type-safe client accessor for {@link $T}.\n", modelType);

        // Fields: executor, registry, dialect, metadata
        classBuilder.addField(JdbcExecutor.class, "executor", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(ModelRegistry.class, "registry", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(SqlDialect.class, "dialect", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(ModelMetadata.class, "metadata", Modifier.PRIVATE, Modifier.FINAL);

        // Constructor
        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JdbcExecutor.class, "executor")
                .addParameter(ModelRegistry.class, "registry")
                .addParameter(SqlDialect.class, "dialect")
                .addStatement("this.executor = $T.requireNonNull(executor, \"executor must not be null\")", Objects.class)
                .addStatement("this.registry = $T.requireNonNull(registry, \"registry must not be null\")", Objects.class)
                .addStatement("this.dialect = $T.requireNonNull(dialect, \"dialect must not be null\")", Objects.class)
                .addStatement("this.metadata = registry.getModel($S)", modelName)
                .build());

        // findMany()
        classBuilder.addMethod(MethodSpec.methodBuilder("findMany")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), modelType))
                .addStatement("return findMany(q -> {})")
                .build());

        // findMany(Consumer<QuerySpec<Filter>>)
        TypeName querySpecType = ParameterizedTypeName.get(ClassName.get("com.graphorm.generator.client", "QuerySpec"), filterType);
        TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), querySpecType);

        MethodSpec.Builder findManySpec = MethodSpec.methodBuilder("findMany")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), modelType))
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T spec = new $T(() -> $T.INSTANCE)", querySpecType, querySpecType, filterType)
                .addStatement("specConsumer.accept(spec)")
                .addStatement("$T.Builder qb = $T.from(metadata.tableName())", SelectQuery.class, SelectQuery.class);

        for (FieldMetadata field : model.fields()) {
            if (!field.isRelation()) {
                String col = field.dbColumnName() != null ? field.dbColumnName() : field.name();
                findManySpec.addStatement("qb.column($S, $S)", col, field.name());
            }
        }
        findManySpec.addStatement("if (spec.getPredicate() != null) qb.where(spec.getPredicate())")
                .addStatement("if (!spec.getOrderBys().isEmpty()) qb.orderBy(spec.getOrderBys())")
                .addStatement("if (spec.getLimit() != null) qb.limit(spec.getLimit())")
                .addStatement("if (spec.getOffset() != null) qb.offset(spec.getOffset())")
                .addStatement("return executor.queryForList(qb.build(), $T.class)", modelType);

        classBuilder.addMethod(findManySpec.build());

        // findUnique
        classBuilder.addMethod(MethodSpec.methodBuilder("findUnique")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), modelType))
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T<QuerySpec<$T>> limited = q -> { specConsumer.accept(q); q.limit(1); }", Consumer.class, filterType)
                .addStatement("$T<$T> list = findMany(limited)", List.class, modelType)
                .addStatement("return list.isEmpty() ? $T.empty() : $T.of(list.getFirst())", Optional.class, Optional.class)
                .build());

        // findFirst
        classBuilder.addMethod(MethodSpec.methodBuilder("findFirst")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), modelType))
                .addParameter(consumerType, "specConsumer")
                .addStatement("return findUnique(specConsumer)")
                .build());

        // findById
        String pkField = model.primaryKeyFields().isEmpty() ? "id" : model.primaryKeyFields().get(0);
        String pkCol = model.findField(pkField).map(FieldMetadata::dbColumnName).orElse(pkField);

        classBuilder.addMethod(MethodSpec.methodBuilder("findById")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), modelType))
                .addParameter(Object.class, "id")
                .addStatement("return findUnique(q -> q.where($T.eq($S, id)))", WherePredicate.class, pkCol)
                .build());

        // count()
        classBuilder.addMethod(MethodSpec.methodBuilder("count")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.LONG)
                .addStatement("return count(q -> {})")
                .build());

        // count(Consumer<QuerySpec<Filter>>)
        classBuilder.addMethod(MethodSpec.methodBuilder("count")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.LONG)
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T spec = new $T(() -> $T.INSTANCE)", querySpecType, querySpecType, filterType)
                .addStatement("specConsumer.accept(spec)")
                .addStatement("$T.Builder qb = $T.from(metadata.tableName()).column($T.of($S, $S))",
                        SelectQuery.class, SelectQuery.class, ProjectedColumn.class, "COUNT(*)", "cnt")
                .addStatement("if (spec.getPredicate() != null) qb.where(spec.getPredicate())")
                .addStatement("$T<$T> results = executor.queryForList(qb.build(), (rs, rowNum) -> rs.getLong(1))", List.class, Long.class)
                .addStatement("return results.isEmpty() ? 0L : results.getFirst()")
                .build());

        // exists
        classBuilder.addMethod(MethodSpec.methodBuilder("exists")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.BOOLEAN)
                .addParameter(consumerType, "specConsumer")
                .addStatement("return count(specConsumer) > 0")
                .build());

        // create(CreateInput)
        MethodSpec.Builder createMethod = MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(modelType)
                .addParameter(createInputType, "createInput")
                .addStatement("$T.ensureWritable(metadata, \"INSERT\")", QueryGuards.class)
                .addStatement("$T.requireNonNull(createInput, \"createInput must not be null\")", Objects.class)
                .addStatement("$T<String, Object> values = createInput.toMap()", Map.class)
                .addStatement("$T insertQuery = $T.into(metadata.tableName()).values(values).build()",
                        InsertQuery.class, InsertQuery.class)
                .addStatement("if (dialect.supportsReturning()) {\n" +
                        "    $T<$T> created = executor.executeInsertAndReturn(insertQuery, $T.class);\n" +
                        "    if (created.isPresent()) return created.get();\n" +
                        "} else {\n" +
                        "    executor.executeUpdate(insertQuery);\n" +
                        "}", Optional.class, modelType, modelType)
                .addStatement("Object pkVal = values.get($S)", pkCol)
                .addStatement("if (pkVal != null) return findById(pkVal).orElseThrow()")
                .addStatement("return findMany(q -> q.limit(1)).stream().findFirst().orElseThrow()");

        classBuilder.addMethod(createMethod.build());

        // update(id, UpdateInput)
        classBuilder.addMethod(MethodSpec.methodBuilder("update")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(modelType)
                .addParameter(Object.class, "id")
                .addParameter(updateInputType, "updateInput")
                .addStatement("$T.ensureWritable(metadata, \"UPDATE\")", QueryGuards.class)
                .addStatement("$T.requireNonNull(id, \"id must not be null\")", Objects.class)
                .addStatement("$T.requireNonNull(updateInput, \"updateInput must not be null\")", Objects.class)
                .addStatement("$T<String, Object> values = updateInput.toMap()", Map.class)
                .addStatement("$T updateQuery = $T.table(metadata.tableName()).values(values).where($T.eq($S, id)).build()",
                        UpdateQuery.class, UpdateQuery.class, WherePredicate.class, pkCol)
                .addStatement("executor.executeUpdate(updateQuery)")
                .addStatement("return findById(id).orElseThrow()")
                .build());

        // delete(id)
        classBuilder.addMethod(MethodSpec.methodBuilder("delete")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.BOOLEAN)
                .addParameter(Object.class, "id")
                .addStatement("$T.ensureWritable(metadata, \"DELETE\")", QueryGuards.class)
                .addStatement("$T.requireNonNull(id, \"id must not be null\")", Objects.class)
                .addStatement("$T deleteQuery = $T.from(metadata.tableName()).where($T.eq($S, id)).build()",
                        DeleteQuery.class, DeleteQuery.class, WherePredicate.class, pkCol)
                .addStatement("return executor.executeUpdate(deleteQuery) > 0")
                .build());

        // deleteMany
        classBuilder.addMethod(MethodSpec.methodBuilder("deleteMany")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.LONG)
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T.ensureWritable(metadata, \"DELETE\")", QueryGuards.class)
                .addStatement("$T spec = new $T(() -> $T.INSTANCE)", querySpecType, querySpecType, filterType)
                .addStatement("specConsumer.accept(spec)")
                .addStatement("$T predicate = spec.getPredicate() == null ? $T.raw($S) : spec.getPredicate()",
                        WherePredicate.class, WherePredicate.class, "1=1")
                .addStatement("$T deleteQuery = $T.from(metadata.tableName()).where(predicate).build()",
                        DeleteQuery.class, DeleteQuery.class)
                .addStatement("return (long) executor.executeUpdate(deleteQuery)")
                .build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, clientClassName, javaFile.toString());
    }

    /**
     * Generates the client accessor for a view model.
     * <p>
     * Implements ONLY {@link ReadOnlyModelAccessor}. Zero write/mutation methods.
     */
    public static GeneratedSourceFile generateViewClient(ViewModelMetadata viewModel, ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(viewModel, "ViewModelMetadata must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.clientPackage();
        String viewName = viewModel.name();
        String clientClassName = viewName + "Client";

        ClassName viewType = ClassName.get(config.modelPackage(), viewName);
        ClassName filterType = ClassName.get(config.filterPackage(), viewName + "Filter");

        TypeName accessorInterface = ParameterizedTypeName.get(
                ClassName.get("com.graphorm.generator.client", "ReadOnlyModelAccessor"),
                viewType,
                filterType
        );

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(clientClassName)
                .addModifiers(Modifier.PUBLIC)
                .addSuperinterface(accessorInterface)
                .addJavadoc("Read-only type-safe client accessor for view {@link $T}.\n" +
                        "<p>\n" +
                        "STRICT READ-ONLY INVARIANT: Completely omits write and mutation methods.\n", viewType);

        classBuilder.addField(JdbcExecutor.class, "executor", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(ModelRegistry.class, "registry", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(SqlDialect.class, "dialect", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(ViewModelMetadata.class, "metadata", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JdbcExecutor.class, "executor")
                .addParameter(ModelRegistry.class, "registry")
                .addParameter(SqlDialect.class, "dialect")
                .addStatement("this.executor = $T.requireNonNull(executor, \"executor must not be null\")", Objects.class)
                .addStatement("this.registry = $T.requireNonNull(registry, \"registry must not be null\")", Objects.class)
                .addStatement("this.dialect = $T.requireNonNull(dialect, \"dialect must not be null\")", Objects.class)
                .addStatement("this.metadata = registry.getViewModel($S)", viewName)
                .build());

        // findMany()
        classBuilder.addMethod(MethodSpec.methodBuilder("findMany")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), viewType))
                .addStatement("return findMany(q -> {})")
                .build());

        // findMany(Consumer<QuerySpec<Filter>>)
        TypeName querySpecType = ParameterizedTypeName.get(ClassName.get("com.graphorm.generator.client", "QuerySpec"), filterType);
        TypeName consumerType = ParameterizedTypeName.get(ClassName.get(Consumer.class), querySpecType);

        MethodSpec.Builder findManySpec = MethodSpec.methodBuilder("findMany")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), viewType))
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T spec = new $T(() -> $T.INSTANCE)", querySpecType, querySpecType, filterType)
                .addStatement("specConsumer.accept(spec)")
                .addStatement("var planner = $T.plan(metadata, registry, dialect)", ViewQueryPlanner.class)
                .addStatement("if (spec.getPredicate() != null) planner.where(spec.getPredicate())")
                .addStatement("for ($T ob : spec.getOrderBys()) planner.orderBy(ob)", OrderByClause.class)
                .addStatement("if (spec.getLimit() != null) planner.limit(spec.getLimit())")
                .addStatement("if (spec.getOffset() != null) planner.offset(spec.getOffset())")
                .addStatement("$T selectQuery = planner.build()", SelectQuery.class)
                .addStatement("return executor.queryForList(selectQuery, $T.class)", viewType);

        classBuilder.addMethod(findManySpec.build());

        // findUnique
        classBuilder.addMethod(MethodSpec.methodBuilder("findUnique")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), viewType))
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T<QuerySpec<$T>> limited = q -> { specConsumer.accept(q); q.limit(1); }", Consumer.class, filterType)
                .addStatement("$T<$T> list = findMany(limited)", List.class, viewType)
                .addStatement("return list.isEmpty() ? $T.empty() : $T.of(list.getFirst())", Optional.class, Optional.class)
                .build());

        // findFirst
        classBuilder.addMethod(MethodSpec.methodBuilder("findFirst")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(ParameterizedTypeName.get(ClassName.get(Optional.class), viewType))
                .addParameter(consumerType, "specConsumer")
                .addStatement("return findUnique(specConsumer)")
                .build());

        // count()
        classBuilder.addMethod(MethodSpec.methodBuilder("count")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.LONG)
                .addStatement("return count(q -> {})")
                .build());

        // count(Consumer<QuerySpec<Filter>>)
        classBuilder.addMethod(MethodSpec.methodBuilder("count")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.LONG)
                .addParameter(consumerType, "specConsumer")
                .addStatement("$T spec = new $T(() -> $T.INSTANCE)", querySpecType, querySpecType, filterType)
                .addStatement("specConsumer.accept(spec)")
                .addStatement("var planner = $T.plan(metadata, registry, dialect)", ViewQueryPlanner.class)
                .addStatement("if (spec.getPredicate() != null) planner.where(spec.getPredicate())")
                .addStatement("$T selectQuery = planner.build()", SelectQuery.class)
                .addStatement("$T innerSql = $T.of(dialect).build(selectQuery)", BoundSql.class, SelectBuilder.class)
                .addStatement("String countSql = \"SELECT COUNT(*) FROM (\" + innerSql.sql() + \") AS _view_count\"")
                .addStatement("$T countBoundSql = new $T(countSql, innerSql.parameters())", BoundSql.class, BoundSql.class)
                .addStatement("$T<$T> results = executor.queryForList(countBoundSql, (rs, rowNum) -> rs.getLong(1))", List.class, Long.class)
                .addStatement("return results.isEmpty() ? 0L : results.getFirst()")
                .build());

        // exists
        classBuilder.addMethod(MethodSpec.methodBuilder("exists")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override.class)
                .returns(TypeName.BOOLEAN)
                .addParameter(consumerType, "specConsumer")
                .addStatement("return count(specConsumer) > 0")
                .build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, clientClassName, javaFile.toString());
    }

    /**
     * Generates the root {@code GraphOrmClient} coordinating access to all models and views.
     */
    public static GeneratedSourceFile generateRootClient(ModelRegistry registry, GeneratorConfig config) {
        Objects.requireNonNull(registry, "ModelRegistry must not be null");
        Objects.requireNonNull(config, "GeneratorConfig must not be null");

        String packageName = config.clientPackage();
        String className = "GraphOrmClient";

        TypeSpec.Builder classBuilder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC)
                .addJavadoc("Prisma-style strongly typed database client generated by GraphORM.\n");

        classBuilder.addField(JdbcExecutor.class, "executor", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(ModelRegistry.class, "registry", Modifier.PRIVATE, Modifier.FINAL);
        classBuilder.addField(SqlDialect.class, "dialect", Modifier.PRIVATE, Modifier.FINAL);

        // Constructor
        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(JdbcExecutor.class, "executor")
                .addParameter(ModelRegistry.class, "registry")
                .addParameter(SqlDialect.class, "dialect")
                .addStatement("this.executor = $T.requireNonNull(executor, \"executor must not be null\")", Objects.class)
                .addStatement("this.registry = $T.requireNonNull(registry, \"registry must not be null\")", Objects.class)
                .addStatement("this.dialect = $T.requireNonNull(dialect, \"dialect must not be null\")", Objects.class)
                .build());

        // Static builder()
        ClassName builderClassName = ClassName.get(packageName, className).nestedClass("Builder");
        classBuilder.addMethod(MethodSpec.methodBuilder("builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(builderClassName)
                .addStatement("return new $T()", builderClassName)
                .build());

        // Convenience factory from DataSource
        classBuilder.addMethod(MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(ClassName.get(packageName, className))
                .addParameter(DataSource.class, "dataSource")
                .addParameter(ModelRegistry.class, "registry")
                .addStatement("return builder().dataSource(dataSource).registry(registry).build()")
                .build());

        // Accessor method for each regular model (e.g. client.student())
        for (ModelMetadata model : registry.getAllModels()) {
            String modelName = model.name();
            String methodName = toLowerCamel(modelName);
            ClassName clientType = ClassName.get(packageName, modelName + "Client");

            classBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(clientType)
                    .addJavadoc("Accessor for {@link $L} model operations.\n", modelName)
                    .addStatement("return new $T(executor, registry, dialect)", clientType)
                    .build());
        }

        // Accessor method for each view model (e.g. client.studentDashboard())
        for (ViewModelMetadata vm : registry.getAllViewModels()) {
            String viewName = vm.name();
            String methodName = toLowerCamel(viewName);
            ClassName clientType = ClassName.get(packageName, viewName + "Client");

            classBuilder.addMethod(MethodSpec.methodBuilder(methodName)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(clientType)
                    .addJavadoc("Read-only accessor for {@link $L} view operations.\n", viewName)
                    .addStatement("return new $T(executor, registry, dialect)", clientType)
                    .build());
        }

        // Inner Builder class
        TypeSpec.Builder builderClass = TypeSpec.classBuilder("Builder")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .addJavadoc("Builder for creating {@link $L} instances.\n", className);

        builderClass.addField(DataSource.class, "dataSource", Modifier.PRIVATE);
        builderClass.addField(JdbcExecutor.class, "executor", Modifier.PRIVATE);
        builderClass.addField(ModelRegistry.class, "registry", Modifier.PRIVATE);
        builderClass.addField(FieldSpec.builder(SqlDialect.class, "dialect", Modifier.PRIVATE)
                .initializer("new $T()", PostgreSqlDialect.class)
                .build());

        builderClass.addMethod(MethodSpec.methodBuilder("dataSource")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(DataSource.class, "dataSource")
                .addStatement("this.dataSource = dataSource; return this")
                .build());

        builderClass.addMethod(MethodSpec.methodBuilder("executor")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(JdbcExecutor.class, "executor")
                .addStatement("this.executor = executor; return this")
                .build());

        builderClass.addMethod(MethodSpec.methodBuilder("registry")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(ModelRegistry.class, "registry")
                .addStatement("this.registry = registry; return this")
                .build());

        builderClass.addMethod(MethodSpec.methodBuilder("dialect")
                .addModifiers(Modifier.PUBLIC)
                .returns(builderClassName)
                .addParameter(SqlDialect.class, "dialect")
                .addStatement("this.dialect = dialect; return this")
                .build());

        builderClass.addMethod(MethodSpec.methodBuilder("build")
                .addModifiers(Modifier.PUBLIC)
                .returns(ClassName.get(packageName, className))
                .addStatement("if (executor == null && dataSource != null) executor = new $T(dataSource, dialect)", JdbcExecutor.class)
                .addStatement("$T.requireNonNull(executor, \"Either executor or dataSource must be configured\")", Objects.class)
                .addStatement("$T.requireNonNull(registry, \"ModelRegistry must not be null\")", Objects.class)
                .addStatement("return new $T(executor, registry, dialect)", ClassName.get(packageName, className))
                .build());

        classBuilder.addType(builderClass.build());

        JavaFile javaFile = JavaFile.builder(packageName, classBuilder.build())
                .indent("    ")
                .skipJavaLangImports(true)
                .build();

        return new GeneratedSourceFile(packageName, className, javaFile.toString());
    }

    private static String toLowerCamel(String str) {
        if (str == null || str.isEmpty()) return str;
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }
}
