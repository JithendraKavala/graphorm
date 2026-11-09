package com.graphorm.schema.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Root AST document representing a fully parsed schema.graphorm file.
 *
 * @param position       document source position
 * @param datasources    declared datasources
 * @param generators     declared generators
 * @param models         declared regular models
 * @param viewModels     declared view models
 * @param compositeTypes declared composite types
 * @param enums          declared enums
 */
public record SchemaDocument(
        SourcePosition position,
        List<DatasourceNode> datasources,
        List<GeneratorNode> generators,
        List<ModelNode> models,
        List<ViewModelNode> viewModels,
        List<CompositeTypeNode> compositeTypes,
        List<EnumNode> enums
) implements AstNode {

    public SchemaDocument {
        Objects.requireNonNull(position, "position must not be null");
        datasources = datasources == null ? List.of() : List.copyOf(datasources);
        generators = generators == null ? List.of() : List.copyOf(generators);
        models = models == null ? List.of() : List.copyOf(models);
        viewModels = viewModels == null ? List.of() : List.copyOf(viewModels);
        compositeTypes = compositeTypes == null ? List.of() : List.copyOf(compositeTypes);
        enums = enums == null ? List.of() : List.copyOf(enums);
    }

    public Optional<DatasourceNode> findDatasource(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return datasources.stream().filter(d -> name.equals(d.name())).findFirst();
    }

    public Optional<GeneratorNode> findGenerator(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return generators.stream().filter(g -> name.equals(g.name())).findFirst();
    }

    public Optional<ModelNode> findModel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return models.stream().filter(m -> name.equals(m.name())).findFirst();
    }

    public boolean containsModel(String name) {
        return findModel(name).isPresent();
    }

    public Optional<ViewModelNode> findViewModel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return viewModels.stream().filter(v -> name.equals(v.name())).findFirst();
    }

    public boolean containsViewModel(String name) {
        return findViewModel(name).isPresent();
    }

    public boolean containsView(String name) {
        return containsViewModel(name);
    }

    public Optional<CompositeTypeNode> findCompositeType(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return compositeTypes.stream().filter(c -> name.equals(c.name())).findFirst();
    }

    public boolean containsCompositeType(String name) {
        return findCompositeType(name).isPresent();
    }

    public Optional<EnumNode> findEnum(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return enums.stream().filter(e -> name.equals(e.name())).findFirst();
    }

    public boolean containsEnum(String name) {
        return findEnum(name).isPresent();
    }

    public boolean containsEntity(String name) {
        return containsModel(name) || containsViewModel(name) || containsCompositeType(name);
    }

    public Optional<AstNode> findEntity(String name) {
        Optional<ModelNode> m = findModel(name);
        if (m.isPresent()) return Optional.of(m.get());
        Optional<ViewModelNode> vm = findViewModel(name);
        if (vm.isPresent()) return Optional.of(vm.get());
        Optional<CompositeTypeNode> ct = findCompositeType(name);
        if (ct.isPresent()) return Optional.of(ct.get());
        return Optional.empty();
    }

    @Override
    public <R> R accept(AstVisitor<R> visitor) {
        return visitor.visitSchemaDocument(this);
    }
}
