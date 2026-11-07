package com.graphorm.jdbc.view;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewJoinMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.GroupByClause;
import com.graphorm.core.query.HavingPredicate;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.JoinConditionParser;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.AggregateType;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Unified query planner for View Models (Stage 1 Database-Backed, Stage 2/3 Virtual Joined Views,
 * Stage 4 Aggregations, GROUP BY, and Join Pruning, and Stage 5 Raw SQL Subquery Views).
 * <p>
 * Handles:
 * <ul>
 *     <li>Source model dependency resolution and dynamic join tree construction.</li>
 *     <li>Aggregation function formatting and expression projection.</li>
 *     <li>Pushdown filter and order-by mapping from view field aliases to source table columns.</li>
 *     <li>HAVING clause mapping and automatic routing of aggregate predicates.</li>
 *     <li>Join pruning optimization when projected fields do not require joined tables.</li>
 *     <li>Automatic omission of GROUP BY when no aggregations are selected.</li>
 *     <li>Raw SQL subquery views (Strategy C: @@query) with pushdown filtering, ordering, and pagination.</li>
 * </ul>
 */
public final class ViewQueryPlanner {

    private static final SqlDialect DEFAULT_DIALECT = new PostgreSqlDialect();

    private static final Set<String> AGGREGATE_PREFIXES = Set.of(
            "COUNT(", "SUM(", "AVG(", "MIN(", "MAX("
    );

    private ViewQueryPlanner() {}

    /**
     * Creates a {@link ViewQueryBuilder} configured for the specified view model and model registry
     * using the default PostgreSQL dialect.
     *
     * @param view     the view model metadata
     * @param registry the model registry containing source and joined models
     * @return a fluent ViewQueryBuilder
     */
    public static ViewQueryBuilder forView(ViewModelMetadata view, ModelRegistry registry) {
        return plan(view, registry, DEFAULT_DIALECT);
    }

    /**
     * Creates a {@link ViewQueryBuilder} configured for the specified view model, model registry, and SQL dialect.
     *
     * @param view     the view model metadata
     * @param registry the model registry
     * @param dialect  the SQL dialect
     * @return a fluent ViewQueryBuilder
     */
    public static ViewQueryBuilder forView(ViewModelMetadata view, ModelRegistry registry, SqlDialect dialect) {
        return plan(view, registry, dialect);
    }

    /**
     * Plans a query for the specified view model using the default PostgreSQL dialect.
     *
     * @param view     the view model metadata
     * @param registry the model registry
     * @return a ViewQueryBuilder
     */
    public static ViewQueryBuilder plan(ViewModelMetadata view, ModelRegistry registry) {
        return plan(view, registry, DEFAULT_DIALECT);
    }

    /**
     * Plans a query for the specified view model with explicit SQL dialect.
     *
     * @param view     the view model metadata
     * @param registry the model registry
     * @param dialect  the SQL dialect
     * @return a ViewQueryBuilder
     */
    public static ViewQueryBuilder plan(ViewModelMetadata view, ModelRegistry registry, SqlDialect dialect) {
        return plan(view, registry, null, dialect);
    }

    /**
     * Plans a query for the specified view model with selective field projection and explicit SQL dialect.
     *
     * @param view           the view model metadata
     * @param registry       the model registry
     * @param selectedFields the subset of view field names to project (null or empty for all fields)
     * @param dialect        the SQL dialect
     * @return a ViewQueryBuilder
     */
    public static ViewQueryBuilder plan(
            ViewModelMetadata view,
            ModelRegistry registry,
            Collection<String> selectedFields,
            SqlDialect dialect
    ) {
        Objects.requireNonNull(view, "ViewModelMetadata must not be null");
        SqlDialect activeDialect = dialect != null ? dialect : DEFAULT_DIALECT;

        return new ViewQueryBuilder(view, registry, activeDialect, selectedFields);
    }

    /**
     * Resolves an identifier like "s.departmentId" into qualified database column "s.department_id".
     */
    static String resolveQualifiedColumn(String identifier, Map<String, ModelMetadata> aliasToModelMap) {
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
     * Builds the field-to-column mapping dictionary for the given view model.
     */
    public static Map<String, String> buildFieldToColumnMap(
            ViewModelMetadata view,
            Map<String, ModelMetadata> aliasToModelMap,
            SqlDialect dialect
    ) {
        Map<String, String> map = new HashMap<>();
        String sourceAlias = view.isRawQuery()
                ? (view.sourceAlias() != null && !view.sourceAlias().isBlank() ? view.sourceAlias() : view.name().toLowerCase())
                : view.sourceAlias();

        for (FieldMetadata field : view.fields()) {
            if (field.isRelation()) {
                continue;
            }

            String expression;
            if (field.isAggregate()) {
                String raw = field.expression() != null ? field.expression() : field.dbColumnName();
                String resolved = resolveQualifiedColumn(raw, aliasToModelMap);
                AggregateType aggType = AggregateType.valueOf(field.aggregateFunction().toUpperCase());
                expression = dialect.buildAggregateSql(aggType, resolved);
            } else if (field.expression() != null && !field.expression().isBlank()) {
                expression = field.expression();
            } else {
                expression = field.dbColumnName();
                if (expression == null || expression.isBlank()) {
                    expression = field.name();
                }
                if (!view.isDatabaseBacked() && sourceAlias != null && !expression.contains(".") && !expression.contains("(")) {
                    expression = sourceAlias + "." + expression;
                }
            }
            map.put(field.name(), expression);
        }
        return map;
    }

    /**
     * Maps view field names in a {@link WherePredicate} to underlying source database column expressions.
     */
    public static WherePredicate mapFilter(WherePredicate predicate, ViewModelMetadata view) {
        return mapFilter(predicate, view, Map.of(), DEFAULT_DIALECT);
    }

    /**
     * Maps view field names in a {@link WherePredicate} to underlying source database column expressions
     * using the model registry and dialect.
     */
    public static WherePredicate mapFilter(
            WherePredicate predicate,
            ViewModelMetadata view,
            Map<String, ModelMetadata> aliasToModelMap,
            SqlDialect dialect
    ) {
        if (predicate == null || view == null) {
            return predicate;
        }
        Map<String, String> fieldToColumnMap = buildFieldToColumnMap(view, aliasToModelMap, dialect);
        return transformPredicate(predicate, fieldToColumnMap);
    }

    /**
     * Maps view field names in order-by clauses to qualified source database columns.
     */
    public static List<OrderByClause> mapOrderBy(List<OrderByClause> orderByList, ViewModelMetadata view) {
        return mapOrderBy(orderByList, view, Map.of(), DEFAULT_DIALECT);
    }

    /**
     * Maps view field names in order-by clauses using the model registry and dialect.
     */
    public static List<OrderByClause> mapOrderBy(
            List<OrderByClause> orderByList,
            ViewModelMetadata view,
            Map<String, ModelMetadata> aliasToModelMap,
            SqlDialect dialect
    ) {
        if (orderByList == null || orderByList.isEmpty() || view == null) {
            return orderByList == null ? List.of() : orderByList;
        }
        Map<String, String> fieldToColumnMap = buildFieldToColumnMap(view, aliasToModelMap, dialect);
        List<OrderByClause> mapped = new ArrayList<>();
        for (OrderByClause ob : orderByList) {
            String expr = ob.expression();
            String mappedExpr = fieldToColumnMap.getOrDefault(expr, expr);
            mapped.add(new OrderByClause(mappedExpr, ob.direction(), ob.nullsOrder()));
        }
        return List.copyOf(mapped);
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
     * Determines whether a predicate targets an aggregate expression.
     */
    private static boolean isAggregatePredicate(WherePredicate predicate) {
        if (predicate == null) {
            return false;
        }
        if (predicate instanceof WherePredicate.BinaryPredicate b) {
            return containsAggregateFunction(b.column());
        }
        if (predicate instanceof WherePredicate.ColumnComparisonPredicate c) {
            return containsAggregateFunction(c.leftColumn()) || containsAggregateFunction(c.rightColumn());
        }
        if (predicate instanceof WherePredicate.InPredicate in) {
            return containsAggregateFunction(in.column());
        }
        if (predicate instanceof WherePredicate.IsNullPredicate isNull) {
            return containsAggregateFunction(isNull.column());
        }
        if (predicate instanceof WherePredicate.BetweenPredicate between) {
            return containsAggregateFunction(between.column());
        }
        if (predicate instanceof WherePredicate.AndPredicate and) {
            return and.predicates().stream().anyMatch(ViewQueryPlanner::isAggregatePredicate);
        }
        if (predicate instanceof WherePredicate.OrPredicate or) {
            return or.predicates().stream().anyMatch(ViewQueryPlanner::isAggregatePredicate);
        }
        if (predicate instanceof WherePredicate.NotPredicate not) {
            return isAggregatePredicate(not.predicate());
        }
        if (predicate instanceof WherePredicate.RawPredicate raw) {
            return containsAggregateFunction(raw.sql());
        }
        return false;
    }

    private static boolean containsAggregateFunction(String expression) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        String upper = expression.toUpperCase();
        for (String prefix : AGGREGATE_PREFIXES) {
            if (upper.contains(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fluent builder for view queries, with automatic pushdown filter, order-by mapping,
     * aggregation formatting, HAVING routing, and join pruning.
     */
    public static final class ViewQueryBuilder {
        private final ViewModelMetadata view;
        private final ModelRegistry registry;
        private final SqlDialect dialect;
        private final Set<String> selectedFields = new LinkedHashSet<>();
        private final List<OrderByClause> orderByList = new ArrayList<>();
        private final Map<String, ModelMetadata> aliasToModelMap = new HashMap<>();

        private WherePredicate wherePredicate;
        private HavingPredicate havingPredicate;
        private Integer limit;
        private Integer offset;

        private ViewQueryBuilder(
                ViewModelMetadata view,
                ModelRegistry registry,
                SqlDialect dialect,
                Collection<String> selectedFields
        ) {
            this.view = view;
            this.registry = registry;
            this.dialect = dialect != null ? dialect : DEFAULT_DIALECT;

            if (selectedFields != null && !selectedFields.isEmpty()) {
                this.selectedFields.addAll(selectedFields);
            }

            // Populate alias map
            if (view.sourceAlias() != null && registry != null && view.sourceModelName() != null) {
                ModelMetadata sourceModel = registry.getModel(view.sourceModelName());
                aliasToModelMap.put(view.sourceAlias(), sourceModel);
            }
            if (registry != null) {
                for (ViewJoinMetadata joinMeta : view.joinDefinitions()) {
                    ModelMetadata joinModel = registry.getModel(joinMeta.targetModel());
                    aliasToModelMap.put(joinMeta.alias(), joinModel);
                }
            }
        }

        public ViewModelMetadata view() {
            return view;
        }

        public ModelRegistry registry() {
            return registry;
        }

        public SqlDialect dialect() {
            return dialect;
        }

        /**
         * Selects only a specific subset of view fields to project in the SQL query.
         *
         * @param fieldNames the view field names to project
         * @return this builder
         */
        public ViewQueryBuilder select(String... fieldNames) {
            if (fieldNames != null) {
                this.selectedFields.addAll(List.of(fieldNames));
            }
            return this;
        }

        /**
         * Selects only a specific subset of view fields to project in the SQL query.
         *
         * @param fieldNames the view field names to project
         * @return this builder
         */
        public ViewQueryBuilder select(Collection<String> fieldNames) {
            if (fieldNames != null) {
                this.selectedFields.addAll(fieldNames);
            }
            return this;
        }

        /**
         * Sets the WHERE predicate, automatically mapping view field names to qualified source columns
         * and routing aggregate predicates to HAVING when necessary.
         *
         * @param predicate the predicate using view field names
         * @return this builder
         */
        public ViewQueryBuilder where(WherePredicate predicate) {
            this.wherePredicate = predicate;
            return this;
        }

        /**
         * Sets the HAVING predicate for filtering aggregated groups.
         *
         * @param predicate the predicate applied after grouping
         * @return this builder
         */
        public ViewQueryBuilder having(WherePredicate predicate) {
            this.havingPredicate = predicate != null ? HavingPredicate.of(predicate) : null;
            return this;
        }

        /**
         * Sets the HAVING predicate for filtering aggregated groups.
         *
         * @param having the having predicate
         * @return this builder
         */
        public ViewQueryBuilder having(HavingPredicate having) {
            this.havingPredicate = having;
            return this;
        }

        /**
         * Adds an order-by clause in ascending order.
         *
         * @param fieldName the view field name
         * @return this builder
         */
        public ViewQueryBuilder orderByAsc(String fieldName) {
            this.orderByList.add(OrderByClause.asc(fieldName));
            return this;
        }

        /**
         * Adds an order-by clause in descending order.
         *
         * @param fieldName the view field name
         * @return this builder
         */
        public ViewQueryBuilder orderByDesc(String fieldName) {
            this.orderByList.add(OrderByClause.desc(fieldName));
            return this;
        }

        /**
         * Adds custom order-by clauses.
         *
         * @param orderBy the order by clauses
         * @return this builder
         */
        public ViewQueryBuilder orderBy(OrderByClause... orderBy) {
            if (orderBy != null) {
                this.orderByList.addAll(List.of(orderBy));
            }
            return this;
        }

        public ViewQueryBuilder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public ViewQueryBuilder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        /**
         * Builds the final optimized {@link SelectQuery} AST, executing join pruning and GROUP BY optimization.
         *
         * @return immutable SelectQuery
         */
        public SelectQuery build() {
            // Strategy C: Raw SQL subquery view (@@query)
            if (view.isRawQuery()) {
                String alias = view.sourceAlias() != null && !view.sourceAlias().isBlank()
                        ? view.sourceAlias()
                        : view.name().toLowerCase();
                SourceTable source = SourceTable.rawSql(view.rawQuery(), alias);
                List<ProjectedColumn> columns = new ArrayList<>();
                for (FieldMetadata field : view.fields()) {
                    if (!field.isRelation()) {
                        if (selectedFields.isEmpty() || selectedFields.contains(field.name())) {
                            String colName = field.dbColumnName() != null ? field.dbColumnName() : field.name();
                            columns.add(ProjectedColumn.of(alias + "." + colName, field.name()));
                        }
                    }
                }
                WherePredicate mappedWhere = mapFilter(wherePredicate, view, aliasToModelMap, dialect);
                List<OrderByClause> mappedOrderBy = mapOrderBy(orderByList, view, aliasToModelMap, dialect);

                return new SelectQuery(
                        source,
                        alias,
                        columns,
                        List.of(),
                        mappedWhere,
                        List.of(),
                        havingPredicate,
                        mappedOrderBy,
                        limit,
                        offset
                );
            }

            // Strategy A: Database-backed view
            if (view.isDatabaseBacked() || view.sourceModelName() == null) {
                SourceTable source = SourceTable.view(view.viewName());
                List<ProjectedColumn> columns = new ArrayList<>();
                for (FieldMetadata field : view.fields()) {
                    if (!field.isRelation()) {
                        if (selectedFields.isEmpty() || selectedFields.contains(field.name())) {
                            columns.add(ProjectedColumn.of(field.dbColumnName(), field.name()));
                        }
                    }
                }
                WherePredicate mappedWhere = mapFilter(wherePredicate, view, aliasToModelMap, dialect);
                List<OrderByClause> mappedOrderBy = mapOrderBy(orderByList, view, aliasToModelMap, dialect);

                return new SelectQuery(
                        source,
                        null,
                        columns,
                        List.of(),
                        mappedWhere,
                        List.of(),
                        havingPredicate,
                        mappedOrderBy,
                        limit,
                        offset
                );
            }

            // Strategy B: GraphORM-defined virtual view
            Objects.requireNonNull(registry, "ModelRegistry is required for virtual view queries");

            ModelMetadata sourceModel = registry.getModel(view.sourceModelName());
            SourceTable sourceTable = SourceTable.table(sourceModel.tableName());
            String sourceAlias = view.sourceAlias();

            // 1. Projected columns
            List<ProjectedColumn> projectedColumns = new ArrayList<>();
            for (FieldMetadata field : view.fields()) {
                if (field.isRelation()) {
                    continue;
                }
                if (!selectedFields.isEmpty() && !selectedFields.contains(field.name())) {
                    continue;
                }

                String expression;
                if (field.isAggregate()) {
                    String raw = field.expression() != null ? field.expression() : field.dbColumnName();
                    String resolved = resolveQualifiedColumn(raw, aliasToModelMap);
                    AggregateType aggType = AggregateType.valueOf(field.aggregateFunction().toUpperCase());
                    expression = dialect.buildAggregateSql(aggType, resolved);
                } else if (field.expression() != null && !field.expression().isBlank()) {
                    expression = field.expression();
                } else {
                    expression = field.dbColumnName();
                    if (expression == null || expression.isBlank()) {
                        expression = sourceAlias != null ? sourceAlias + "." + field.name() : field.name();
                    } else if (!expression.contains(".") && !expression.contains("(") && sourceAlias != null) {
                        expression = sourceAlias + "." + expression;
                    }
                }
                projectedColumns.add(ProjectedColumn.of(expression, field.name()));
            }

            // 2. Joins
            List<JoinClause> allJoins = new ArrayList<>();
            for (ViewJoinMetadata joinMeta : view.joinDefinitions()) {
                ModelMetadata joinModel = registry.getModel(joinMeta.targetModel());
                SourceTable targetTable = SourceTable.table(joinModel.tableName());

                WherePredicate joinCondition = JoinConditionParser.parse(
                        joinMeta.onCondition(),
                        ident -> resolveQualifiedColumn(ident, aliasToModelMap)
                );

                allJoins.add(new JoinClause(joinMeta.joinType(), targetTable, joinMeta.alias(), joinCondition));
            }

            // 3. Filter mapping & automatic HAVING separation
            WherePredicate mappedWhere = mapFilter(wherePredicate, view, aliasToModelMap, dialect);
            HavingPredicate mappedHaving = havingPredicate != null
                    ? HavingPredicate.of(mapFilter(havingPredicate.predicate(), view, aliasToModelMap, dialect))
                    : null;

            // Separate aggregate predicates from WHERE to HAVING if present
            WherePredicate finalWhere = mappedWhere;
            HavingPredicate finalHaving = mappedHaving;

            if (mappedWhere != null && isAggregatePredicate(mappedWhere)) {
                FilterPartition partition = partitionFilter(mappedWhere);
                finalWhere = partition.wherePart;
                if (partition.havingPart != null) {
                    if (finalHaving == null) {
                        finalHaving = HavingPredicate.of(partition.havingPart);
                    } else {
                        finalHaving = HavingPredicate.of(finalHaving.predicate().and(partition.havingPart));
                    }
                }
            }

            // 4. Order By mapping
            List<OrderByClause> mappedOrderBy = mapOrderBy(orderByList, view, aliasToModelMap, dialect);

            // 5. Group By mapping
            List<GroupByClause> declaredGroupBy = new ArrayList<>();
            for (String groupCol : view.groupByColumns()) {
                String resolved = resolveQualifiedColumn(groupCol, aliasToModelMap);
                declaredGroupBy.add(GroupByClause.of(resolved));
            }

            // 6. Join Pruning and GROUP BY Optimization
            JoinPruner.PruningResult pruned = JoinPruner.prune(
                    sourceAlias,
                    allJoins,
                    projectedColumns,
                    finalWhere,
                    finalHaving,
                    mappedOrderBy,
                    declaredGroupBy
            );

            return new SelectQuery(
                    sourceTable,
                    sourceAlias,
                    projectedColumns,
                    pruned.joins(),
                    finalWhere,
                    pruned.groupBy(),
                    finalHaving,
                    mappedOrderBy,
                    limit,
                    offset
            );
        }

        /**
         * Creates a {@link SelectQuery.Builder} populated with this planned query.
         *
         * @return SelectQuery.Builder
         */
        public SelectQuery.Builder toSelectQueryBuilder() {
            SelectQuery sq = build();
            SelectQuery.Builder b = SelectQuery.from(sq.source()).as(sq.tableAlias());
            for (ProjectedColumn pc : sq.columns()) {
                b.column(pc);
            }
            for (JoinClause j : sq.joins()) {
                b.join(j);
            }
            if (sq.where() != null) {
                b.where(sq.where());
            }
            for (GroupByClause gb : sq.groupBy()) {
                b.groupBy(gb);
            }
            if (sq.having() != null) {
                b.having(sq.having());
            }
            for (OrderByClause ob : sq.orderBy()) {
                b.orderBy(ob);
            }
            if (sq.limit() != null) {
                b.limit(sq.limit());
            }
            if (sq.offset() != null) {
                b.offset(sq.offset());
            }
            return b;
        }

        private record FilterPartition(WherePredicate wherePart, WherePredicate havingPart) {}

        private FilterPartition partitionFilter(WherePredicate predicate) {
            if (predicate instanceof WherePredicate.AndPredicate and) {
                List<WherePredicate> whereChildren = new ArrayList<>();
                List<WherePredicate> havingChildren = new ArrayList<>();
                for (WherePredicate p : and.predicates()) {
                    if (isAggregatePredicate(p)) {
                        havingChildren.add(p);
                    } else {
                        whereChildren.add(p);
                    }
                }
                WherePredicate w = whereChildren.isEmpty() ? null
                        : (whereChildren.size() == 1 ? whereChildren.getFirst() : WherePredicate.and(whereChildren));
                WherePredicate h = havingChildren.isEmpty() ? null
                        : (havingChildren.size() == 1 ? havingChildren.getFirst() : WherePredicate.and(havingChildren));
                return new FilterPartition(w, h);
            } else if (isAggregatePredicate(predicate)) {
                return new FilterPartition(null, predicate);
            } else {
                return new FilterPartition(predicate, null);
            }
        }
    }
}
