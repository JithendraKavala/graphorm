package com.graphorm.generator.engine;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Configuration options for the code generation engine.
 */
public record GeneratorConfig(
        String basePackage,
        String modelPackage,
        String inputPackage,
        String filterPackage,
        String clientPackage,
        String metadataPackage,
        boolean generateBuilders,
        boolean generateWithMethods,
        Path outputDirectory
) {

    public GeneratorConfig {
        Objects.requireNonNull(basePackage, "basePackage must not be null");
        if (modelPackage == null) modelPackage = basePackage + ".model";
        if (inputPackage == null) inputPackage = basePackage + ".input";
        if (filterPackage == null) filterPackage = basePackage + ".filter";
        if (clientPackage == null) clientPackage = basePackage + ".client";
        if (metadataPackage == null) metadataPackage = basePackage + ".metadata";
    }

    public static Builder builder() {
        return new Builder();
    }

    public static GeneratorConfig of(String basePackage) {
        return builder().basePackage(basePackage).build();
    }

    public static class Builder {
        private String basePackage = "com.graphorm.generated";
        private String modelPackage;
        private String inputPackage;
        private String filterPackage;
        private String clientPackage;
        private String metadataPackage;
        private boolean generateBuilders = true;
        private boolean generateWithMethods = true;
        private Path outputDirectory;

        public Builder basePackage(String basePackage) {
            this.basePackage = Objects.requireNonNull(basePackage);
            return this;
        }

        public Builder modelPackage(String modelPackage) {
            this.modelPackage = modelPackage;
            return this;
        }

        public Builder inputPackage(String inputPackage) {
            this.inputPackage = inputPackage;
            return this;
        }

        public Builder filterPackage(String filterPackage) {
            this.filterPackage = filterPackage;
            return this;
        }

        public Builder clientPackage(String clientPackage) {
            this.clientPackage = clientPackage;
            return this;
        }

        public Builder metadataPackage(String metadataPackage) {
            this.metadataPackage = metadataPackage;
            return this;
        }

        public Builder generateBuilders(boolean generateBuilders) {
            this.generateBuilders = generateBuilders;
            return this;
        }

        public Builder generateWithMethods(boolean generateWithMethods) {
            this.generateWithMethods = generateWithMethods;
            return this;
        }

        public Builder outputDirectory(Path outputDirectory) {
            this.outputDirectory = outputDirectory;
            return this;
        }

        public GeneratorConfig build() {
            return new GeneratorConfig(
                    basePackage,
                    modelPackage,
                    inputPackage,
                    filterPackage,
                    clientPackage,
                    metadataPackage,
                    generateBuilders,
                    generateWithMethods,
                    outputDirectory
            );
        }
    }
}
