package com.graphorm.core.metadata;

import com.graphorm.core.exception.EntityNotFoundException;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe, immutable in-memory registry holding all schema-defined models,
 * view models, composite types, and enums.
 * <p>
 * Built once during application startup by schema validation and metadata transformation,
 * and remains strictly immutable at runtime with zero synchronization overhead.
 */
public final class ModelRegistry {

    private final Map<String, ModelMetadata> models;
    private final Map<String, ViewModelMetadata> viewModels;
    private final Map<String, CompositeTypeMetadata> compositeTypes;
    private final Map<String, EnumMetadata> enums;
    private final Map<String, EntityMetadata> allEntities;

    private ModelRegistry(Builder builder) {
        this.models = Map.copyOf(builder.models);
        this.viewModels = Map.copyOf(builder.viewModels);
        this.compositeTypes = Map.copyOf(builder.compositeTypes);
        this.enums = Map.copyOf(builder.enums);

        Map<String, EntityMetadata> combined = new LinkedHashMap<>();
        combined.putAll(this.models);
        combined.putAll(this.viewModels);
        combined.putAll(this.compositeTypes);
        this.allEntities = Collections.unmodifiableMap(combined);
    }

    /**
     * Looks up a model by name.
     *
     * @param name model name
     * @return an {@link Optional} containing the model if found
     */
    public Optional<ModelMetadata> findModel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(models.get(name));
    }

    /**
     * Retrieves a model by name, throwing {@link EntityNotFoundException} if missing.
     *
     * @param name model name
     * @return the model metadata
     * @throws EntityNotFoundException if no model with the given name exists
     */
    public ModelMetadata getModel(String name) {
        return findModel(name)
                .orElseThrow(() -> new EntityNotFoundException(name, "Model '" + name + "' not found in ModelRegistry"));
    }

    /**
     * Looks up a view model by name.
     *
     * @param name view model name
     * @return an {@link Optional} containing the view model if found
     */
    public Optional<ViewModelMetadata> findViewModel(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(viewModels.get(name));
    }

    /**
     * Retrieves a view model by name, throwing {@link EntityNotFoundException} if missing.
     *
     * @param name view model name
     * @return the view model metadata
     * @throws EntityNotFoundException if no view model with the given name exists
     */
    public ViewModelMetadata getViewModel(String name) {
        return findViewModel(name)
                .orElseThrow(() -> new EntityNotFoundException(name, "View model '" + name + "' not found in ModelRegistry"));
    }

    /**
     * Looks up a composite type by name.
     *
     * @param name composite type name
     * @return an {@link Optional} containing the composite type if found
     */
    public Optional<CompositeTypeMetadata> findCompositeType(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(compositeTypes.get(name));
    }

    /**
     * Retrieves a composite type by name, throwing {@link EntityNotFoundException} if missing.
     *
     * @param name composite type name
     * @return the composite type metadata
     * @throws EntityNotFoundException if no composite type with the given name exists
     */
    public CompositeTypeMetadata getCompositeType(String name) {
        return findCompositeType(name)
                .orElseThrow(() -> new EntityNotFoundException(name, "Composite type '" + name + "' not found in ModelRegistry"));
    }

    /**
     * Looks up an enum by name.
     *
     * @param name enum name
     * @return an {@link Optional} containing the enum metadata if found
     */
    public Optional<EnumMetadata> findEnum(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(enums.get(name));
    }

    /**
     * Retrieves an enum by name, throwing {@link EntityNotFoundException} if missing.
     *
     * @param name enum name
     * @return the enum metadata
     * @throws EntityNotFoundException if no enum with the given name exists
     */
    public EnumMetadata getEnum(String name) {
        return findEnum(name)
                .orElseThrow(() -> new EntityNotFoundException(name, "Enum '" + name + "' not found in ModelRegistry"));
    }

    /**
     * Looks up any top-level entity (model, view model, or composite type) by name.
     *
     * @param name entity name
     * @return an {@link Optional} containing the entity metadata if found
     */
    public Optional<EntityMetadata> findEntity(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(allEntities.get(name));
    }

    /**
     * Retrieves an entity by name, throwing {@link EntityNotFoundException} if missing.
     *
     * @param name entity name
     * @return the entity metadata
     * @throws EntityNotFoundException if no entity with the given name exists
     */
    public EntityMetadata getEntity(String name) {
        return findEntity(name)
                .orElseThrow(() -> new EntityNotFoundException(name, "Entity '" + name + "' not found in ModelRegistry"));
    }

    /**
     * Checks if a model exists in the registry.
     *
     * @param name model name
     * @return true if registered
     */
    public boolean containsModel(String name) {
        return models.containsKey(name);
    }

    /**
     * Checks if a view model exists in the registry.
     *
     * @param name view model name
     * @return true if registered
     */
    public boolean containsViewModel(String name) {
        return viewModels.containsKey(name);
    }

    /**
     * Checks if a composite type exists in the registry.
     *
     * @param name composite type name
     * @return true if registered
     */
    public boolean containsCompositeType(String name) {
        return compositeTypes.containsKey(name);
    }

    /**
     * Checks if an enum exists in the registry.
     *
     * @param name enum name
     * @return true if registered
     */
    public boolean containsEnum(String name) {
        return enums.containsKey(name);
    }

    /**
     * Checks if any entity exists with the specified name.
     *
     * @param name entity name
     * @return true if registered
     */
    public boolean containsEntity(String name) {
        return allEntities.containsKey(name);
    }

    /**
     * Returns an unmodifiable collection of all registered regular models.
     *
     * @return collection of model metadata
     */
    public Collection<ModelMetadata> getAllModels() {
        return models.values();
    }

    /**
     * Returns an unmodifiable collection of all registered view models.
     *
     * @return collection of view model metadata
     */
    public Collection<ViewModelMetadata> getAllViewModels() {
        return viewModels.values();
    }

    /**
     * Alias for {@link #getAllViewModels()}.
     *
     * @return collection of view model metadata
     */
    public Collection<ViewModelMetadata> getAllViews() {
        return getAllViewModels();
    }

    /**
     * Returns an unmodifiable collection of all registered composite types.
     *
     * @return collection of composite type metadata
     */
    public Collection<CompositeTypeMetadata> getAllCompositeTypes() {
        return compositeTypes.values();
    }

    /**
     * Returns an unmodifiable collection of all registered enums.
     *
     * @return collection of enum metadata
     */
    public Collection<EnumMetadata> getAllEnums() {
        return enums.values();
    }

    /**
     * Returns an unmodifiable collection of all entities (models, views, composite types).
     *
     * @return collection of entity metadata
     */
    public Collection<EntityMetadata> getAllEntities() {
        return allEntities.values();
    }

    /**
     * Returns the total count of registered entities (models + views + composite types).
     *
     * @return total entity count
     */
    public int size() {
        return allEntities.size();
    }

    /**
     * Checks if the registry is empty.
     *
     * @return true if no entities are registered
     */
    public boolean isEmpty() {
        return allEntities.isEmpty();
    }

    /**
     * Creates a new builder for constructing a {@link ModelRegistry}.
     *
     * @return a new Builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ModelRegistry}.
     */
    public static final class Builder {
        private final Map<String, ModelMetadata> models = new LinkedHashMap<>();
        private final Map<String, ViewModelMetadata> viewModels = new LinkedHashMap<>();
        private final Map<String, CompositeTypeMetadata> compositeTypes = new LinkedHashMap<>();
        private final Map<String, EnumMetadata> enums = new LinkedHashMap<>();

        private Builder() {}

        public Builder addModel(ModelMetadata model) {
            Objects.requireNonNull(model, "Model must not be null");
            validateUniqueName(model.name());
            this.models.put(model.name(), model);
            return this;
        }

        public Builder addViewModel(ViewModelMetadata viewModel) {
            Objects.requireNonNull(viewModel, "ViewModel must not be null");
            validateUniqueName(viewModel.name());
            this.viewModels.put(viewModel.name(), viewModel);
            return this;
        }

        public Builder addCompositeType(CompositeTypeMetadata compositeType) {
            Objects.requireNonNull(compositeType, "CompositeType must not be null");
            validateUniqueName(compositeType.name());
            this.compositeTypes.put(compositeType.name(), compositeType);
            return this;
        }

        public Builder addEnum(EnumMetadata enumMetadata) {
            Objects.requireNonNull(enumMetadata, "Enum must not be null");
            if (this.enums.containsKey(enumMetadata.enumName())) {
                throw new IllegalArgumentException(
                        "Duplicate enum declaration: '" + enumMetadata.enumName() + "' is already registered");
            }
            this.enums.put(enumMetadata.enumName(), enumMetadata);
            return this;
        }

        public Builder models(Collection<ModelMetadata> models) {
            if (models != null) {
                for (ModelMetadata model : models) {
                    addModel(model);
                }
            }
            return this;
        }

        public Builder viewModels(Collection<ViewModelMetadata> viewModels) {
            if (viewModels != null) {
                for (ViewModelMetadata vm : viewModels) {
                    addViewModel(vm);
                }
            }
            return this;
        }

        public Builder compositeTypes(Collection<CompositeTypeMetadata> compositeTypes) {
            if (compositeTypes != null) {
                for (CompositeTypeMetadata ct : compositeTypes) {
                    addCompositeType(ct);
                }
            }
            return this;
        }

        public Builder enums(Collection<EnumMetadata> enums) {
            if (enums != null) {
                for (EnumMetadata e : enums) {
                    addEnum(e);
                }
            }
            return this;
        }

        private void validateUniqueName(String entityName) {
            if (models.containsKey(entityName)
                    || viewModels.containsKey(entityName)
                    || compositeTypes.containsKey(entityName)) {
                throw new IllegalArgumentException(
                        "Duplicate entity declaration: '" + entityName + "' is already registered");
            }
        }

        public ModelRegistry build() {
            return new ModelRegistry(this);
        }
    }
}
