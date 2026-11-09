package com.graphorm.core.metadata;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable metadata record representing a regular model mapped 1:1 to a physical database table.
 * Supports full CRUD operations (or read-only projection if annotated with @@readonly),
 * multi-tenant isolation, and multi-tier caching metadata.
 *
 * @param name              the declared model name (e.g. "Student")
 * @param tableName         the physical database table name (e.g. "students")
 * @param fields            the immutable list of declared field metadata
 * @param primaryKeyFields  the field names forming the primary key
 * @param uniqueConstraints the list of unique constraint column sets
 * @param relations         the declared relationships originating from this model
 * @param isReadOnly        whether this physical model is read-only (declared via @@readonly)
 * @param authRoles         the authorization roles required to access this model (declared via @@auth)
 * @param indexes           the database index metadata (declared via @@index or @index)
 * @param checkConstraints  the check constraint metadata (declared via @@check)
 * @param schemaName        the database schema/namespace (e.g. "public", "tenant_a")
 * @param exposure          granular exposure control for GraphQL APIs (declared via @@expose)
 * @param isMultiTenant     whether this model is multi-tenant partitioned
 * @param tenantColumn      the physical column name holding tenant IDs
 * @param cacheMetadata     caching configuration (declared via @cached or @@cached)
 */
public record ModelMetadata(
        String name,
        String tableName,
        List<FieldMetadata> fields,
        List<String> primaryKeyFields,
        List<List<String>> uniqueConstraints,
        List<RelationMetadata> relations,
        boolean isReadOnly,
        List<String> authRoles,
        List<IndexMetadata> indexes,
        List<CheckConstraintMetadata> checkConstraints,
        String schemaName,
        ExposureMetadata exposure,
        boolean isMultiTenant,
        String tenantColumn,
        CacheMetadata cacheMetadata
) implements EntityMetadata {

    /**
     * Compact constructor enforcing immutability and defensive copying.
     */
    public ModelMetadata {
        Objects.requireNonNull(name, "Model name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Model name must not be blank");
        }
        if (tableName == null || tableName.isBlank()) {
            tableName = name.toLowerCase();
        }

        fields = fields == null ? List.of() : List.copyOf(fields);
        primaryKeyFields = primaryKeyFields == null ? List.of() : List.copyOf(primaryKeyFields);
        uniqueConstraints = uniqueConstraints == null
                ? List.of()
                : List.copyOf(uniqueConstraints.stream().map(List::copyOf).toList());
        relations = relations == null ? List.of() : List.copyOf(relations);
        authRoles = authRoles == null ? List.of() : List.copyOf(authRoles);
        indexes = indexes == null ? List.of() : List.copyOf(indexes);
        checkConstraints = checkConstraints == null ? List.of() : List.copyOf(checkConstraints);
        if (schemaName != null && schemaName.isBlank()) {
            schemaName = null;
        }
        if (exposure == null) {
            exposure = ExposureMetadata.of(true, !isReadOnly);
        }
        if (!isMultiTenant && fields.stream().anyMatch(FieldMetadata::isTenantId)) {
            isMultiTenant = true;
        }
        if (tenantColumn == null && isMultiTenant) {
            tenantColumn = fields.stream()
                    .filter(FieldMetadata::isTenantId)
                    .map(FieldMetadata::dbColumnName)
                    .findFirst()
                    .orElse(null);
        }
        if (cacheMetadata == null) {
            cacheMetadata = CacheMetadata.NOT_CACHED;
        }
    }

    /**
     * Backward-compatible constructor without cacheMetadata.
     */
    public ModelMetadata(
            String name,
            String tableName,
            List<FieldMetadata> fields,
            List<String> primaryKeyFields,
            List<List<String>> uniqueConstraints,
            List<RelationMetadata> relations,
            boolean isReadOnly,
            List<String> authRoles,
            List<IndexMetadata> indexes,
            List<CheckConstraintMetadata> checkConstraints,
            String schemaName,
            ExposureMetadata exposure,
            boolean isMultiTenant,
            String tenantColumn
    ) {
        this(name, tableName, fields, primaryKeyFields, uniqueConstraints, relations, isReadOnly, authRoles,
                indexes, checkConstraints, schemaName, exposure, isMultiTenant, tenantColumn, CacheMetadata.NOT_CACHED);
    }

    /**
     * Backward-compatible constructor without isMultiTenant and tenantColumn.
     */
    public ModelMetadata(
            String name,
            String tableName,
            List<FieldMetadata> fields,
            List<String> primaryKeyFields,
            List<List<String>> uniqueConstraints,
            List<RelationMetadata> relations,
            boolean isReadOnly,
            List<String> authRoles,
            List<IndexMetadata> indexes,
            List<CheckConstraintMetadata> checkConstraints,
            String schemaName,
            ExposureMetadata exposure
    ) {
        this(name, tableName, fields, primaryKeyFields, uniqueConstraints, relations, isReadOnly, authRoles,
                indexes, checkConstraints, schemaName, exposure, false, null, CacheMetadata.NOT_CACHED);
    }

    /**
     * Constructor without exposure metadata.
     */
    public ModelMetadata(
            String name,
            String tableName,
            List<FieldMetadata> fields,
            List<String> primaryKeyFields,
            List<List<String>> uniqueConstraints,
            List<RelationMetadata> relations,
            boolean isReadOnly,
            List<String> authRoles,
            List<IndexMetadata> indexes,
            List<CheckConstraintMetadata> checkConstraints,
            String schemaName
    ) {
        this(name, tableName, fields, primaryKeyFields, uniqueConstraints, relations, isReadOnly, authRoles,
                indexes, checkConstraints, schemaName, ExposureMetadata.of(true, !isReadOnly), false, null, CacheMetadata.NOT_CACHED);
    }

    /**
     * Constructor without indexes, check constraints, schemaName, and exposure.
     */
    public ModelMetadata(
            String name,
            String tableName,
            List<FieldMetadata> fields,
            List<String> primaryKeyFields,
            List<List<String>> uniqueConstraints,
            List<RelationMetadata> relations,
            boolean isReadOnly,
            List<String> authRoles
    ) {
        this(name, tableName, fields, primaryKeyFields, uniqueConstraints, relations, isReadOnly, authRoles,
                List.of(), List.of(), null, ExposureMetadata.of(true, !isReadOnly), false, null, CacheMetadata.NOT_CACHED);
    }

    /**
     * Backward-compatible constructor without isReadOnly and authRoles.
     */
    public ModelMetadata(
            String name,
            String tableName,
            List<FieldMetadata> fields,
            List<String> primaryKeyFields,
            List<List<String>> uniqueConstraints,
            List<RelationMetadata> relations
    ) {
        this(name, tableName, fields, primaryKeyFields, uniqueConstraints, relations, false, List.of());
    }

    /**
     * Backward-compatible constructor without authRoles.
     */
    public ModelMetadata(
            String name,
            String tableName,
            List<FieldMetadata> fields,
            List<String> primaryKeyFields,
            List<List<String>> uniqueConstraints,
            List<RelationMetadata> relations,
            boolean isReadOnly
    ) {
        this(name, tableName, fields, primaryKeyFields, uniqueConstraints, relations, isReadOnly, List.of());
    }

    @Override
    public EntityType entityType() {
        return EntityType.MODEL;
    }

    @Override
    public boolean isReadOnly() {
        return isReadOnly;
    }

    @Override
    public List<String> authRoles() {
        return authRoles;
    }

    @Override
    public boolean hasAuthRoles() {
        return authRoles != null && !authRoles.isEmpty();
    }

    @Override
    public ExposureMetadata exposure() {
        return exposure;
    }

    @Override
    public boolean exposeQueries() {
        return exposure.queries();
    }

    @Override
    public boolean exposeMutations() {
        return exposure.mutations() && !isReadOnly;
    }

    @Override
    public boolean isMultiTenant() {
        return isMultiTenant;
    }

    @Override
    public String tenantColumn() {
        if (tenantColumn != null) {
            return tenantColumn;
        }
        return tenantField().map(FieldMetadata::dbColumnName).orElse(null);
    }

    @Override
    public CacheMetadata cacheMetadata() {
        return cacheMetadata;
    }

    /**
     * Looks up the optimistic locking version field on this model, if declared.
     */
    public Optional<FieldMetadata> versionField() {
        return fields.stream()
                .filter(FieldMetadata::isVersion)
                .findFirst();
    }

    /**
     * Indicates whether this model has an optimistic locking version field.
     */
    public boolean hasVersionField() {
        return versionField().isPresent();
    }

    /**
     * Checks if the given field name is part of the primary key.
     *
     * @param fieldName the field name to check
     * @return true if the field is a primary key component
     */
    public boolean isPrimaryKey(String fieldName) {
        if (fieldName == null) {
            return false;
        }
        return primaryKeyFields.contains(fieldName);
    }

    /**
     * Looks up an index by its index name.
     *
     * @param indexName the index name to look up
     * @return an Optional containing the IndexMetadata, or empty if not found
     */
    public Optional<IndexMetadata> findIndex(String indexName) {
        if (indexName == null) {
            return Optional.empty();
        }
        return indexes.stream()
                .filter(idx -> indexName.equals(idx.name()))
                .findFirst();
    }

    /**
     * Checks if an index with the specified name exists on this model.
     *
     * @param indexName the index name to check
     * @return true if the index exists, false otherwise
     */
    public Optional<CheckConstraintMetadata> findCheckConstraint(String constraintName) {
        if (constraintName == null) {
            return Optional.empty();
        }
        return checkConstraints.stream()
                .filter(chk -> chk.name().equals(constraintName))
                .findFirst();
    }

    public boolean hasIndex(String indexName) {
        return findIndex(indexName).isPresent();
    }

    /**
     * Checks if any index (explicit or implicit unique) covers the exact specified columns in order.
     *
     * @param columnNames the ordered list of column names
     * @return true if a matching index exists
     */
    public boolean hasIndexOn(List<String> columnNames) {
        if (columnNames == null || columnNames.isEmpty()) {
            return false;
        }
        // 1. Check primary key match
        if (primaryKeyFields.equals(columnNames)) {
            return true;
        }
        // 2. Check declared indexes
        for (IndexMetadata idx : indexes) {
            if (idx.fields().equals(columnNames)) {
                return true;
            }
        }
        // 3. Check unique constraints
        for (List<String> uc : uniqueConstraints) {
            if (uc.equals(columnNames)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Looks up a relation by its declared name.
     *
     * @param relationName the relation name to look up
     * @return an Optional containing the RelationMetadata, or empty if not found
     */
    public Optional<RelationMetadata> findRelation(String relationName) {
        if (relationName == null) {
            return Optional.empty();
        }
        return relations.stream()
                .filter(r -> relationName.equals(r.relationName()))
                .findFirst();
    }

    /**
     * Retrieves a relation by its declared name, or throws {@link IllegalArgumentException} if not found.
     *
     * @param relationName the relation name to retrieve
     * @return the relation metadata
     * @throws IllegalArgumentException if no relation with the given name exists
     */
    public RelationMetadata getRelation(String relationName) {
        return findRelation(relationName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Relation '" + relationName + "' not found on model '" + name + "'"));
    }

    /**
     * Checks if a relation with the specified name exists on this model.
     *
     * @param relationName the relation name to check
     * @return true if the relation exists, false otherwise
     */
    public boolean hasRelation(String relationName) {
        return findRelation(relationName).isPresent();
    }

    /**
     * Creates a new builder for {@link ModelMetadata}.
     *
     * @param name the model name
     * @return a new Builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Fluent builder for {@link ModelMetadata}.
     */
    public static final class Builder {
        private final String name;
        private String tableName;
        private final List<FieldMetadata> fields = new ArrayList<>();
        private final List<String> primaryKeyFields = new ArrayList<>();
        private final List<List<String>> uniqueConstraints = new ArrayList<>();
        private final List<RelationMetadata> relations = new ArrayList<>();
        private boolean isReadOnly = false;
        private final List<String> authRoles = new ArrayList<>();
        private final List<IndexMetadata> indexes = new ArrayList<>();
        private final List<CheckConstraintMetadata> checkConstraints = new ArrayList<>();
        private String schemaName;
        private ExposureMetadata exposure;
        private boolean isMultiTenant = false;
        private String tenantColumn;
        private CacheMetadata cacheMetadata = CacheMetadata.NOT_CACHED;

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "Model name must not be null");
        }

        public Builder tableName(String tableName) {
            this.tableName = tableName;
            return this;
        }

        public Builder addField(FieldMetadata field) {
            this.fields.add(Objects.requireNonNull(field, "Field must not be null"));
            return this;
        }

        public Builder fields(List<FieldMetadata> fields) {
            this.fields.clear();
            if (fields != null) {
                this.fields.addAll(fields);
            }
            return this;
        }

        public Builder addPrimaryKeyField(String fieldName) {
            this.primaryKeyFields.add(Objects.requireNonNull(fieldName, "Primary key field must not be null"));
            return this;
        }

        public Builder primaryKeyFields(List<String> primaryKeyFields) {
            this.primaryKeyFields.clear();
            if (primaryKeyFields != null) {
                this.primaryKeyFields.addAll(primaryKeyFields);
            }
            return this;
        }

        public Builder addUniqueConstraint(List<String> constraintFields) {
            Objects.requireNonNull(constraintFields, "Constraint fields must not be null");
            this.uniqueConstraints.add(List.copyOf(constraintFields));
            return this;
        }

        public Builder addUniqueConstraint(String... constraintFields) {
            Objects.requireNonNull(constraintFields, "Constraint fields must not be null");
            this.uniqueConstraints.add(List.of(constraintFields));
            return this;
        }

        public Builder uniqueConstraints(List<List<String>> uniqueConstraints) {
            this.uniqueConstraints.clear();
            if (uniqueConstraints != null) {
                for (List<String> constraint : uniqueConstraints) {
                    this.uniqueConstraints.add(List.copyOf(constraint));
                }
            }
            return this;
        }

        public Builder addRelation(RelationMetadata relation) {
            this.relations.add(Objects.requireNonNull(relation, "Relation must not be null"));
            return this;
        }

        public Builder relations(List<RelationMetadata> relations) {
            this.relations.clear();
            if (relations != null) {
                this.relations.addAll(relations);
            }
            return this;
        }

        public Builder readOnly(boolean isReadOnly) {
            this.isReadOnly = isReadOnly;
            return this;
        }

        public Builder isReadOnly(boolean isReadOnly) {
            this.isReadOnly = isReadOnly;
            return this;
        }

        public Builder authRoles(List<String> roles) {
            this.authRoles.clear();
            if (roles != null) {
                this.authRoles.addAll(roles);
            }
            return this;
        }

        public Builder addAuthRole(String role) {
            if (role != null && !role.isBlank()) {
                this.authRoles.add(role.trim());
            }
            return this;
        }

        public Builder authRoles(String... roles) {
            this.authRoles.clear();
            if (roles != null) {
                for (String r : roles) {
                    if (r != null && !r.isBlank()) {
                        this.authRoles.add(r.trim());
                    }
                }
            }
            return this;
        }

        public Builder addIndex(IndexMetadata index) {
            this.indexes.add(Objects.requireNonNull(index, "Index must not be null"));
            return this;
        }

        public Builder indexes(List<IndexMetadata> indexes) {
            this.indexes.clear();
            if (indexes != null) {
                this.indexes.addAll(indexes);
            }
            return this;
        }

        public Builder addCheckConstraint(CheckConstraintMetadata checkConstraint) {
            this.checkConstraints.add(Objects.requireNonNull(checkConstraint, "CheckConstraint must not be null"));
            return this;
        }

        public Builder checkConstraints(List<CheckConstraintMetadata> checkConstraints) {
            this.checkConstraints.clear();
            if (checkConstraints != null) {
                this.checkConstraints.addAll(checkConstraints);
            }
            return this;
        }

        public Builder schemaName(String schemaName) {
            this.schemaName = schemaName;
            return this;
        }

        public Builder exposure(ExposureMetadata exposure) {
            this.exposure = exposure;
            return this;
        }

        public Builder expose(boolean queries, boolean mutations) {
            this.exposure = ExposureMetadata.of(queries, mutations);
            return this;
        }

        public Builder multiTenant(boolean isMultiTenant) {
            this.isMultiTenant = isMultiTenant;
            return this;
        }

        public Builder tenantColumn(String tenantColumn) {
            this.tenantColumn = tenantColumn;
            if (tenantColumn != null) {
                this.isMultiTenant = true;
            }
            return this;
        }

        public Builder cacheMetadata(CacheMetadata cacheMetadata) {
            this.cacheMetadata = cacheMetadata != null ? cacheMetadata : CacheMetadata.NOT_CACHED;
            return this;
        }

        public Builder cached(boolean cached) {
            if (!cached) {
                this.cacheMetadata = CacheMetadata.NOT_CACHED;
            } else if (!this.cacheMetadata.isCached()) {
                this.cacheMetadata = CacheMetadata.of(null, Set.of(name));
            }
            return this;
        }

        public Builder cache(Duration ttl, Set<String> tags) {
            this.cacheMetadata = CacheMetadata.of(ttl, tags);
            return this;
        }

        public ModelMetadata build() {
            ExposureMetadata finalExposure = exposure != null
                    ? exposure
                    : ExposureMetadata.of(true, !isReadOnly);

            return new ModelMetadata(
                    name,
                    tableName,
                    fields,
                    primaryKeyFields,
                    uniqueConstraints,
                    relations,
                    isReadOnly,
                    authRoles,
                    indexes,
                    checkConstraints,
                    schemaName,
                    finalExposure,
                    isMultiTenant,
                    tenantColumn,
                    cacheMetadata
            );
        }
    }
}
