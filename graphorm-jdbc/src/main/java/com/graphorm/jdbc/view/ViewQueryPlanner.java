package com.graphorm.jdbc.view;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewJoinMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.query.JoinConditionParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Unified query planner for View Models (Stage 1 Database-Backed and Stage 2/3 Virtual Joined Views).
 * <p>
 * Handles:
 * <ul>
 *     <li>Source model dependency resolution and dynamic join tree construction.</li>
 *     <li>Field mapping and qualified column alias projection.</li>
 *     <li>Pushdown filter and order-by mapping from view field aliases to source table columns.</li>
 * </ul>
 */
public final class ViewQueryPlanner {

    private ViewQueryPlanner() {}

    /**
     * Creates a {@link ViewQueryBuilder} configured for the specified view model and model registry.
     *
     * @param view     the view model metadata
     * @param registry the model registry containing source and joined models
     * @return a fluent ViewQueryBuilder pre-configured with projection and joins
     */
    public static ViewQueryBuilder forView(ViewModelMetadata view, ModelRegistry registry) {
        return plan(view, registry);
    }

    /**
     * Plans a query for the specified view model, constructing the join tree and projections.
     *
     * @param view     the view model metadata
     * @param registry the model registry
     * @return a ViewQueryBuilder
     */
    public static ViewQueryBuilder plan(ViewModelMetadata view, ModelRegistry registry) {
        Objects.requireNonNull(view, "ViewModelMetadata must not be null");

        // Strategy A: Database-backed view
        if (view.isDatabaseBacked() || view.sourceModelName() == null) {
            SourceTable source = SourceTable.view(view.viewName());
            SelectQuery.Builder sqBuilder = SelectQuery.from(source);
            for (FieldMetadata field : view.fields()) {
                if (!field.isRelation()) {
                    sqBuilder.column(field.dbColumnName(), field.name());
                }
            }
            return new ViewQueryBuilder(view, registry, sqBuilder);
        }

        // Strategy B: GraphORM-defined virtual view
        Objects.requireNonNull(registry, "ModelRegistry is required for virtual view queries");

        ModelMetadata sourceModel = registry.getModel(view.sourceModelName());
        SourceTable source = SourceTable.table(sourceModel.tableName());
        String sourceAlias = view.sourceAlias();

        SelectQuery.Builder sqBuilder = SelectQuery.from(source);
        if (sourceAlias != null && !sourceAlias.isBlank()) {
            sqBuilder.as(sourceAlias);
        }

        // 1. Projection columns
        for (FieldMetadata field : view.fields()) {
            if (!field.isRelation()) {
                String expression = field.dbColumnName();
                if (expression == null || expression.isBlank()) {
                    expression = sourceAlias != null ? sourceAlias + "." + field.name() : field.name();
                } else if (!expression.contains(".") && !expression.contains("(") && sourceAlias != null) {
                    expression = sourceAlias + "." + expression;
                }
                sqBuilder.column(expression, field.name());
            }
        }

        // 2. Joins
        Map<String, ModelMetadata> aliasToModelMap = new HashMap<>();
        if (sourceAlias != null) {
            aliasToModelMap.put(sourceAlias, sourceModel);
        }

        for (ViewJoinMetadata joinMeta : view.joinDefinitions()) {
            ModelMetadata joinModel = registry.getModel(joinMeta.targetModel());
            aliasToModelMap.put(joinMeta.alias(), joinModel);
        }

        for (ViewJoinMetadata joinMeta : view.joinDefinitions()) {
            ModelMetadata joinModel = registry.getModel(joinMeta.targetModel());
            SourceTable targetTable = SourceTable.table(joinModel.tableName());

            WherePredicate joinCondition = JoinConditionParser.parse(
                    joinMeta.onCondition(),
                    ident -> resolveQualifiedColumn(ident, aliasToModelMap)
            );

            sqBuilder.join(new JoinClause(joinMeta.joinType(), targetTable, joinMeta.alias(), joinCondition));
        }

        return new ViewQueryBuilder(view, registry, sqBuilder);
    }

    /**
     * Resolves an identifier like "s.departmentId" into qualified database column "s.department_id".
     */
    private static String resolveQualifiedColumn(String identifier, Map<String, ModelMetadata> aliasToModelMap) {
        if (identifier == null) {
            return null;
        }
        int dot = identifier.indexOf('.');
        if (dot > 0 && dot < identifier.length() - 1) {
            String alias = identifier.substring(0, dot);
            String fieldName = identifier.substring(dot + 1);
            ModelMetadata model = aliasToModelMap.get(alias);
            if (model != null && model.hasField(fieldName)) {
                String dbCol = model.getField(fieldName).dbColumnName();
                return alias + "." + dbCol;
            }
        }
        return identifier;
    }

    /**
     * Maps view field names in a {@link WherePredicate} to underlying source database column expressions.
     *
     * @param predicate the predicate using view field names (e.g. "studentName == 'Alice'")
     * @param view      the view metadata
     * @return the transformed predicate with source column expressions (e.g. "s.name == 'Alice'")
     */
    public static WherePredicate mapFilter(WherePredicate predicate, ViewModelMetadata view) {
        if (predicate == null || view == null) {
            return predicate;
        }
        Map<String, String> fieldToColumnMap = buildFieldToColumnMap(view);
        return transformPredicate(predicate, fieldToColumnMap);
    }

    /**
     * Maps view field names in order-by clauses to qualified source database columns.
     *
     * @param orderByList the order-by clauses
     * @param view        the view metadata
     * @return the mapped order-by clauses
     */
    public static List<OrderByClause> mapOrderBy(List<OrderByClause> orderByList, ViewModelMetadata view) {
        if (orderByList == null || orderByList.isEmpty() || view == null) {
            return orderByList == null ? List.of() : orderByList;
        }
        Map<String, String> fieldToColumnMap = buildFieldToColumnMap(view);
        List<OrderByClause> mapped = new ArrayList<>();
        for (OrderByClause ob : orderByList) {
            String expr = ob.expression();
            String mappedExpr = fieldToColumnMap.getOrDefault(expr, expr);
            mapped.add(new OrderByClause(mappedExpr, ob.direction(), ob.nullsOrder()));
        }
        return List.copyOf(mapped);
    }

    private static Map<String, String> buildFieldToColumnMap(ViewModelMetadata view) {
        Map<String, String> map = new HashMap<>();
        String sourceAlias = view.sourceAlias();

        for (FieldMetadata field : view.fields()) {
            String dbCol = field.dbColumnName();
            if (dbCol == null || dbCol.isBlank()) {
                dbCol = field.name();
            }
            if (!view.isDatabaseBacked() && sourceAlias != null && !dbCol.contains(".") && !dbCol.contains("(")) {
                dbCol = sourceAlias + "." + dbCol;
            }
            map.put(field.name(), dbCol);
        }
        return map;
    }

    private static WherePredicate transformPredicate(WherePredicate predicate, Map<String, String> columnMap) {
        if (predicate instanceof WherePredicate.BinaryPredicate binary) {
            String mapped = columnMap.getOrDefault(binary.column(), binary.column());
            return new WherePredicate.BinaryPredicate(mapped, binary.operator(), binary.value());
        }
        if (predicate instanceof WherePredicate.ColumnComparisonPredicate colComp) {
            String mappedLeft = columnMap.getOrDefault(colComp.leftColumn(), colComp.leftColumn());
            String mappedRight = columnMap.getOrDefault(colComp.rightColumn(), colComp.rightColumn());
            return new WherePredicate.ColumnComparisonPredicate(mappedLeft, colComp.operator(), mappedRight);
        }
        if (predicate instanceof WherePredicate.InPredicate in) {
            String mapped = columnMap.getOrDefault(in.column(), in.column());
            return new WherePredicate.InPredicate(mapped, in.values(), in.isNotIn());
        }
        if (predicate instanceof WherePredicate.IsNullPredicate isNull) {
            String mapped = columnMap.getOrDefault(isNull.column(), isNull.column());
            return new WherePredicate.IsNullPredicate(mapped, isNull.isNull());
        }
        if (predicate instanceof WherePredicate.BetweenPredicate between) {
            String mapped = columnMap.getOrDefault(between.column(), between.column());
            return new WherePredicate.BetweenPredicate(mapped, between.lower(), between.upper());
        }
        if (predicate instanceof WherePredicate.AndPredicate and) {
            List<WherePredicate> children = new ArrayList<>();
            for (WherePredicate p : and.predicates()) {
                children.add(transformPredicate(p, columnMap));
            }
            return WherePredicate.and(children);
        }
        if (predicate instanceof WherePredicate.OrPredicate or) {
            List<WherePredicate> children = new ArrayList<>();
            for (WherePredicate p : or.predicates()) {
                children.add(transformPredicate(p, columnMap));
            }
            return WherePredicate.or(children);
        }
        if (predicate instanceof WherePredicate.NotPredicate not) {
            return WherePredicate.not(transformPredicate(not.predicate(), columnMap));
        }
        if (predicate instanceof WherePredicate.RawPredicate) {
            return predicate;
        }
        return predicate;
    }

    /**
     * Fluent builder for view queries, with automatic pushdown filter and order-by mapping.
     */
    public static final class ViewQueryBuilder {
        private final ViewModelMetadata view;
        private final ModelRegistry registry;
        private final SelectQuery.Builder innerBuilder;

        private ViewQueryBuilder(ViewModelMetadata view, ModelRegistry registry, SelectQuery.Builder innerBuilder) {
            this.view = view;
            this.registry = registry;
            this.innerBuilder = innerBuilder;
        }

        public ViewModelMetadata view() {
            return view;
        }

        public ModelRegistry registry() {
            return registry;
        }

        public SelectQuery.Builder toSelectQueryBuilder() {
            return innerBuilder;
        }

        /**
         * Sets the WHERE predicate, automatically mapping view field names to qualified source columns.
         *
         * @param predicate the predicate using view field names
         * @return this builder
         */
        public ViewQueryBuilder where(WherePredicate predicate) {
            if (predicate != null) {
                WherePredicate mapped = mapFilter(predicate, view);
                innerBuilder.where(mapped);
            }
            return this;
        }

        /**
         * Adds an order-by clause, automatically mapping the view field name to qualified source column.
         *
         * @param fieldName the view field name
         * @return this builder
         */
        public ViewQueryBuilder orderByAsc(String fieldName) {
            String mapped = buildFieldToColumnMap(view).getOrDefault(fieldName, fieldName);
            innerBuilder.orderByAsc(mapped);
            return this;
        }

        /**
         * Adds an order-by clause in descending order, mapping the field name.
         *
         * @param fieldName the view field name
         * @return this builder
         */
        public ViewQueryBuilder orderByDesc(String fieldName) {
            String mapped = buildFieldToColumnMap(view).getOrDefault(fieldName, fieldName);
            innerBuilder.orderByDesc(mapped);
            return this;
        }

        /**
         * Adds custom order-by clauses, mapping field names.
         *
         * @param orderBy the order by clauses
         * @return this builder
         */
        public ViewQueryBuilder orderBy(OrderByClause... orderBy) {
            if (orderBy != null) {
                for (OrderByClause ob : mapOrderBy(List.of(orderBy), view)) {
                    innerBuilder.orderBy(ob);
                }
            }
            return this;
        }

        public ViewQueryBuilder limit(Integer limit) {
            innerBuilder.limit(limit);
            return this;
        }

        public ViewQueryBuilder offset(Integer offset) {
            innerBuilder.offset(offset);
            return this;
        }

        /**
         * Builds the final {@link SelectQuery} AST.
         *
         * @return immutable SelectQuery
         */
        public SelectQuery build() {
            return innerBuilder.build();
        }
    }
}
