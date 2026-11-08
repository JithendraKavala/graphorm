package com.graphorm.core.metadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Immutable metadata record representing a view model in GraphORM.
 * Supports Strategy A (Database-Backed views via @@view),
 * Strategy B (GraphORM-Defined virtual joined views via @@from, @@leftJoin, @@groupBy),
 * and Strategy C (Raw SQL subquery views via @@query).
 * <p>
 * Invariant: View models are strictly read-only; {@link #isReadOnly()} always returns {@code true}.
 *
 * @param name              the declared view model name (e.g. "StudentDashboard")
 * @param viewName          the mapped physical database view name (e.g. "vw_student_summary"), if any
 * @param fields            the immutable list of declared field metadata
 * @param sourceModelName   the root source model name (from @@from)
 * @param sourceAlias       the table alias for the source model (e.g. "s")
 * @param joinDefinitions   the immutable list of declared view joins
 * @param groupByColumns    the immutable list of column expressions for GROUP BY
 * @param isDatabaseBacked  true if mapped to a physical database view (Strategy A)
 * @param isMaterialized    true if the physical database view is materialized
 * @param rawQuery          for raw query views: raw SQL SELECT/WITH query (Strategy C, @@query)
 * @param authRoles         the authorization roles required to access this view (declared via @@auth)
 */
public record ViewModelMetadata(
        String name,
        String viewName,
        List<FieldMetadata> fields,
        String sourceModelName,
        String sourceAlias,
        List<ViewJoinMetadata> joinDefinitions,
        List<String> groupByColumns,
        boolean isDatabaseBacked,
        boolean isMaterialized,
        String rawQuery,
        List<String> authRoles
) implements EntityMetadata {

    /**
     * Compact constructor enforcing immutability and defensive copying.
     */
    public ViewModelMetadata {
        Objects.requireNonNull(name, "View model name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("View model name must not be blank");
        }
        if (viewName == null || viewName.isBlank()) {
            viewName = name.toLowerCase();
        }

        fields = fields == null ? List.of() : List.copyOf(fields);
        joinDefinitions = joinDefinitions == null ? List.of() : List.copyOf(joinDefinitions);
        groupByColumns = groupByColumns == null ? List.of() : List.copyOf(groupByColumns);
        authRoles = authRoles == null ? List.of() : List.copyOf(authRoles);
    }

    /**
     * Backward-compatible constructor without authRoles.
     */
    public ViewModelMetadata(
            String name,
            String viewName,
            List<FieldMetadata> fields,
            String sourceModelName,
            String sourceAlias,
            List<ViewJoinMetadata> joinDefinitions,
            List<String> groupByColumns,
            boolean isDatabaseBacked,
            boolean isMaterialized,
            String rawQuery
    ) {
        this(name, viewName, fields, sourceModelName, sourceAlias, joinDefinitions, groupByColumns, isDatabaseBacked, isMaterialized, rawQuery, List.of());
    }

    /**
     * Backward-compatible constructor without rawQuery and authRoles.
     */
    public ViewModelMetadata(
            String name,
            String viewName,
            List<FieldMetadata> fields,
            String sourceModelName,
            String sourceAlias,
            List<ViewJoinMetadata> joinDefinitions,
            List<String> groupByColumns,
            boolean isDatabaseBacked,
            boolean isMaterialized
    ) {
        this(name, viewName, fields, sourceModelName, sourceAlias, joinDefinitions, groupByColumns, isDatabaseBacked, isMaterialized, null, List.of());
    }

    @Override
    public EntityType entityType() {
        return EntityType.VIEW;
    }

    /**
     * Strict architectural invariant: View models are always read-only.
     *
     * @return true always
     */
    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public List<String> authRoles() {
        return authRoles;
    }

    @Override
    public boolean hasAuthRoles() {
        return authRoles != null && !authRoles.isEmpty();
    }

    /**
     * Indicates whether this view model is defined via a raw SQL query (Strategy C).
     *
     * @return true if raw SQL query view
     */
    public boolean isRawQuery() {
        return rawQuery != null && !rawQuery.isBlank();
    }

    /**
     * Indicates whether this view model is a virtual join view (Strategy B).
     *
     * @return true if virtual join view
     */
    public boolean isVirtual() {
        return !isDatabaseBacked && !isRawQuery();
    }

    /**
     * Creates a new builder for {@link ViewModelMetadata}.
     *
     * @param name the view model name
     * @return a new Builder instance
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    /**
     * Fluent builder for {@link ViewModelMetadata}.
     */
    public static final class Builder {
        private final String name;
        private String viewName;
        private final List<FieldMetadata> fields = new ArrayList<>();
        private String sourceModelName;
        private String sourceAlias;
        private final List<ViewJoinMetadata> joinDefinitions = new ArrayList<>();
        private final List<String> groupByColumns = new ArrayList<>();
        private boolean isDatabaseBacked;
        private boolean isMaterialized;
        private String rawQuery;
        private final List<String> authRoles = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "View model name must not be null");
        }

        public Builder viewName(String viewName) {
            this.viewName = viewName;
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

        public Builder sourceModel(String sourceModelName, String sourceAlias) {
            this.sourceModelName = sourceModelName;
            this.sourceAlias = sourceAlias;
            return this;
        }

        public Builder addJoin(ViewJoinMetadata join) {
            this.joinDefinitions.add(Objects.requireNonNull(join, "Join must not be null"));
            return this;
        }

        public Builder joins(List<ViewJoinMetadata> joins) {
            this.joinDefinitions.clear();
            if (joins != null) {
                this.joinDefinitions.addAll(joins);
            }
            return this;
        }

        public Builder addGroupBy(String columnExpression) {
            this.groupByColumns.add(Objects.requireNonNull(columnExpression, "Group by expression must not be null"));
            return this;
        }

        public Builder groupBy(List<String> groupByColumns) {
            this.groupByColumns.clear();
            if (groupByColumns != null) {
                this.groupByColumns.addAll(groupByColumns);
            }
            return this;
        }

        public Builder groupBy(String... groupByColumns) {
            this.groupByColumns.clear();
            if (groupByColumns != null) {
                this.groupByColumns.addAll(List.of(groupByColumns));
            }
            return this;
        }

        public Builder databaseBacked(boolean isDatabaseBacked) {
            this.isDatabaseBacked = isDatabaseBacked;
            return this;
        }

        public Builder materialized(boolean isMaterialized) {
            this.isMaterialized = isMaterialized;
            if (isMaterialized) {
                this.isDatabaseBacked = true;
            }
            return this;
        }

        public Builder rawQuery(String rawQuery) {
            this.rawQuery = rawQuery;
            return this;
        }

        public Builder authRoles(List<String> roles) {
            this.authRoles.clear();
            if (roles != null) {
                this.authRoles.addAll(roles);
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

        public Builder addAuthRole(String role) {
            if (role != null && !role.isBlank()) {
                this.authRoles.add(role.trim());
            }
            return this;
        }

        public ViewModelMetadata build() {
            return new ViewModelMetadata(
                    name,
                    viewName,
                    fields,
                    sourceModelName,
                    sourceAlias,
                    joinDefinitions,
                    groupByColumns,
                    isDatabaseBacked,
                    isMaterialized,
                    rawQuery,
                    List.copyOf(authRoles)
            );
        }
    }
}
