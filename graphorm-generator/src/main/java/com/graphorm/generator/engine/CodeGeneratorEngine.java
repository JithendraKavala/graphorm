package com.graphorm.generator.engine;

import com.graphorm.core.metadata.*;
import com.graphorm.generator.client.ClientApiGenerator;
import com.graphorm.generator.filter.FilterCriteriaGenerator;
import com.graphorm.generator.input.InputDtoGenerator;
import com.graphorm.generator.metadata.MetadataGenerator;
import com.graphorm.generator.model.CompositeTypeGenerator;
import com.graphorm.generator.model.EnumGenerator;
import com.graphorm.generator.model.ModelRecordGenerator;
import com.graphorm.generator.model.ViewModelRecordGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Orchestrating code generation engine for GraphORM.
 * <p>
 * Translates an immutable {@link ModelRegistry} into strongly-typed Java 21 artifacts:
 * <ul>
 *     <li>Immutable model records with component accessors, with-methods, and builders</li>
 *     <li>Read-only view model projection records (zero mutation methods)</li>
 *     <li>Composite value object records</li>
 *     <li>Schema enums</li>
 *     <li>CreateInput and UpdateInput DTOs strictly for regular models</li>
 *     <li>Type-safe filter criteria builders for models and views</li>
 *     <li>Static metadata descriptors</li>
 *     <li>Prisma-style {@code GraphOrmClient} and individual model/view accessors</li>
 * </ul>
 * Guarantees 100% deterministic, byte-identical output across runs.
 */
public final class CodeGeneratorEngine {

    private final GeneratorConfig config;

    public CodeGeneratorEngine(GeneratorConfig config) {
        this.config = Objects.requireNonNull(config, "GeneratorConfig must not be null");
    }

    public static CodeGeneratorEngine create(GeneratorConfig config) {
        return new CodeGeneratorEngine(config);
    }

    public static CodeGeneratorEngine create(String basePackage) {
        return new CodeGeneratorEngine(GeneratorConfig.of(basePackage));
    }

    public GeneratorConfig getConfig() {
        return config;
    }

    /**
     * Generates all source files from the given model registry.
     *
     * @param registry the model registry
     * @return list of generated source files, sorted deterministically
     */
    public List<GeneratedSourceFile> generateAll(ModelRegistry registry) {
        Objects.requireNonNull(registry, "ModelRegistry must not be null");

        List<GeneratedSourceFile> files = new ArrayList<>();

        // 1. Enums (sorted by enumName)
        List<EnumMetadata> enums = new ArrayList<>(registry.getAllEnums());
        enums.sort(Comparator.comparing(EnumMetadata::enumName));
        for (EnumMetadata em : enums) {
            files.add(EnumGenerator.generate(em, config));
        }

        // 2. Composite Types (sorted by name)
        List<CompositeTypeMetadata> composites = new ArrayList<>(registry.getAllCompositeTypes());
        composites.sort(Comparator.comparing(CompositeTypeMetadata::name));
        for (CompositeTypeMetadata ct : composites) {
            files.add(CompositeTypeGenerator.generate(ct, registry, config));
        }

        // 3. Regular Models (sorted by name)
        List<ModelMetadata> models = new ArrayList<>(registry.getAllModels());
        models.sort(Comparator.comparing(ModelMetadata::name));
        for (ModelMetadata model : models) {
            files.add(ModelRecordGenerator.generate(model, registry, config));
            files.add(InputDtoGenerator.generateCreateInput(model, registry, config));
            files.add(InputDtoGenerator.generateUpdateInput(model, registry, config));
            files.add(FilterCriteriaGenerator.generate(model, registry, config));
            files.add(MetadataGenerator.generate(model, registry, config));
            files.add(ClientApiGenerator.generateModelClient(model, registry, config));
        }

        // 4. View Models (sorted by name, strictly read-only, NO input DTOs)
        List<ViewModelMetadata> views = new ArrayList<>(registry.getAllViewModels());
        views.sort(Comparator.comparing(ViewModelMetadata::name));
        for (ViewModelMetadata vm : views) {
            files.add(ViewModelRecordGenerator.generate(vm, registry, config));
            files.add(FilterCriteriaGenerator.generate(vm, registry, config));
            files.add(MetadataGenerator.generate(vm, registry, config));
            files.add(ClientApiGenerator.generateViewClient(vm, registry, config));
        }

        // 5. Root GraphOrmClient
        files.add(ClientApiGenerator.generateRootClient(registry, config));

        // Deterministic sorting by fully-qualified class name
        files.sort(Comparator.comparing(GeneratedSourceFile::fullyQualifiedName));

        return Collections.unmodifiableList(files);
    }

    /**
     * Generates all source files and writes them to the specified output directory.
     *
     * @param registry  the model registry
     * @param outputDir root directory where package trees will be created
     * @return list of Paths of all written files
     * @throws IOException if writing any file fails
     */
    public List<Path> writeAll(ModelRegistry registry, Path outputDir) throws IOException {
        Objects.requireNonNull(outputDir, "Output directory must not be null");
        List<GeneratedSourceFile> files = generateAll(registry);
        List<Path> writtenPaths = new ArrayList<>(files.size());

        for (GeneratedSourceFile file : files) {
            Path p = file.writeTo(outputDir);
            writtenPaths.add(p);
        }

        return Collections.unmodifiableList(writtenPaths);
    }
}
