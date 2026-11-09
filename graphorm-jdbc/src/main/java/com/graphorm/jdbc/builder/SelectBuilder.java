package com.graphorm.jdbc.builder;

import com.graphorm.core.metadata.EntityMetadata;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.ViewModelMetadata;
import com.graphorm.core.pagination.CursorCodec;
import com.graphorm.core.pagination.CursorPagination;
import com.graphorm.core.pagination.KeysetPredicateGenerator;
import com.graphorm.core.query.CommonTableExpression;
import com.graphorm.core.query.GroupByClause;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.QueryGuards;
import com.graphorm.core.query.SelectQuery;
import com.graphorm.core.query.SourceTable;
import com.graphorm.core.query.WherePredicate;
import com.graphorm.core.query.template.SqlFragment;
import com.graphorm.jdbc.cache.FilterShapeExtractor;
import com.graphorm.jdbc.cache.QueryPlan;
import com.graphorm.jdbc.cache.QueryPlanCache;
import com.graphorm.jdbc.cache.QueryPlanKey;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import com.graphorm.jdbc.view.ViewQueryPlanner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds parameterized {@link BoundSql} from {@link SelectQuery} ASTs across both
 * regular models and read-only view models.
 * Supports Common Table Expressions (CTEs), keyset cursor pagination with deterministic tie-breaker ordering,
 * multi-table joins, aggregations, pushdown execution, and L1 {@link QueryPlanCache} pre-compiled template lookup.
 */
public final class SelectBuilder {

    private final SqlDialect dialect;
    private final QueryPlanCache planCache;

    public SelectBuilder(SqlDialect dialect) {
        this(dialect, QueryPlanCache.getDefault());
    }

    public SelectBuilder(SqlDialect dialect, QueryPlanCache planCache) {
        this.dialect = Objects.requireNonNull(dialect, "SqlDialect must not be null");
        this.planCache = planCache;
    }

    /**
     * Factory method creating a new {@link SelectBuilder} with the given dialect.
     *
     * @param dialect the dialect to use
     * @return a new SelectBuilder instance
     */
    public static SelectBuilder of(SqlDialect dialect) {
        return new SelectBuilder(dialect, QueryPlanCache.getDefault());
    }

    /**
     * Factory method creating a new {@link SelectBuilder} with the given dialect and query plan cache.
     *
     * @param dialect   the dialect to use
     * @param planCache the plan cache (may be null to disable plan caching)
     * @return a new SelectBuilder instance
     */
    public static SelectBuilder of(SqlDialect dialect, QueryPlanCache planCache) {
        return new SelectBuilder(dialect, planCache);
    }

    /**
     * Creates a {@link SelectQuery.Builder} pre-populated with an entity's persistent columns
     * and target table/view.
     *
     * @param entity the entity metadata (Model or ViewModel)
     * @return a configured SelectQuery.Builder
     */
    public static SelectQuery.Builder forEntity(EntityMetadata entity) {
        return forEntity(entity, null);
    }

    /**
     * Creates a {@link SelectQuery.Builder} pre-populated with an entity's columns, joins,
     * and target tables, utilizing the {@link ModelRegistry} for virtual joined view models.
     *
     * @param entity   the entity metadata (Model or ViewModel)
     * @param registry the model registry (required for virtual views with joins)
     * @return a configured SelectQuery.Builder
     */
    public static SelectQuery.Builder forEntity(EntityMetadata entity, ModelRegistry registry) {
        Objects.requireNonNull(entity, "Entity metadata must not be null");

        if (entity instanceof ViewModelMetadata vm) {
            if (vm.isRawQuery() || (!vm.isDatabaseBacked() && registry != null)) {
                return ViewQueryPlanner.plan(vm, registry).toSelectQueryBuilder();
            }
        }

        String tableName;
        SourceTable source;
        if (entity instanceof ModelMetadata model) {
            tableName = model.tableName();
            source = SourceTable.table(tableName);
        } else if (entity instanceof ViewModelMetadata vm) {
            tableName = vm.viewName();
            source = SourceTable.view(tableName);
        } else {
            tableName = entity.name().toLowerCase();
            source = entity.isReadOnly()
                    ? SourceTable.view(tableName)
                    : SourceTable.table(tableName);
        }

        SelectQuery.Builder builder = SelectQuery.from(source);
        for (FieldMetadata field : entity.fields()) {
            if (!field.isRelation()) {
                builder.column(field.dbColumnName(), field.name());
            }
        }
        if (entity.isMultiTenant()) {
            String tenantId = QueryGuards.requireTenantContext(entity, "SELECT");
            if (tenantId != null) {
                builder.tenantWhere(WherePredicate.equal(entity.tenantColumn(), tenantId));
            }
        }
        return builder;
    }

    /**
     * Builds a {@link BoundSql} from a {@link SelectQuery}, using the {@link QueryPlanCache} when available.
     *
     * @param query the select query AST
     * @return the parameterized BoundSql
     */
    public BoundSql build(SelectQuery query) {
        Objects.requireNonNull(query, "SelectQuery must not be null");

        // Bypass plan cache for dynamic cursor pagination or complex subqueries
        if (planCache == null || query.hasCursorPagination() || query.hasCtes()
                || query.source() instanceof SourceTable.SubquerySource
                || query.source() instanceof SourceTable.RawSqlSource
                || query.joins().stream().anyMatch(j -> j.table() instanceof SourceTable.SubquerySource || j.table() instanceof SourceTable.RawSqlSource)) {
            return doBuild(query);
        }

        QueryPlanKey key = FilterShapeExtractor.createKey(query, dialect.getClass().getSimpleName());
        QueryPlan plan = planCache.get(key);
        if (plan != null) {
            List<Object> parameters = extractParameters(query);
            return new BoundSql(plan.sqlTemplate(), parameters);
        }

        BoundSql boundSql = doBuild(query);
        planCache.put(key, QueryPlan.of(boundSql.sql(), query.columns(), boundSql.parameters().size()));
        return boundSql;
    }

    /**
     * Extracts all runtime parameter values in order from a {@link SelectQuery} AST.
     *
     * @param query the select query AST
     * @return list of parameter values
     */
    public List<Object> extractParameters(SelectQuery query) {
        List<Object> parameters = new ArrayList<>();
        // Joins
        for (JoinClause join : query.joins()) {
            if (join.condition() != null) {
                parameters.addAll(WhereClauseBuilder.extractParameters(join.condition()));
            }
        }
        // WHERE
        if (query.where() != null) {
            parameters.addAll(WhereClauseBuilder.extractParameters(query.where()));
        }
        // HAVING
        if (query.having() != null && query.having().predicate() != null) {
            parameters.addAll(WhereClauseBuilder.extractParameters(query.having().predicate()));
        }
        // LIMIT and OFFSET
        if (query.limit() != null || query.offset() != null) {
            String limitOffsetClause = dialect.formatLimitOffset(query.limit(), query.offset());
            if (!limitOffsetClause.isBlank()) {
                if (query.limit() != null) {
                    parameters.add(query.limit());
                }
                if (query.offset() != null) {
                    parameters.add(query.offset());
                }
            }
        }
        return parameters;
    }

    private BoundSql doBuild(SelectQuery query) {
        StringBuilder sql = new StringBuilder();
        List<Object> parameters = new ArrayList<>();

        // 0. Common Table Expressions (WITH ... AS (...))
        if (query.hasCtes()) {
            boolean recursive = query.ctes().stream().anyMatch(CommonTableExpression::recursive);
            sql.append("WITH ");
            if (recursive) {
                sql.append("RECURSIVE ");
            }
            for (int i = 0; i < query.ctes().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                CommonTableExpression cte = query.ctes().get(i);
                sql.append(dialect.quoteIdentifier(cte.name()));
                if (!cte.columnAliases().isEmpty()) {
                    sql.append(" (");
                    for (int j = 0; j < cte.columnAliases().size(); j++) {
                        if (j > 0) sql.append(", ");
                        sql.append(dialect.quoteIdentifier(cte.columnAliases().get(j)));
                    }
                    sql.append(")");
                }
                sql.append(" AS (");
                if (cte.query() instanceof SelectQuery sq) {
                    BoundSql cteSql = build(sq);
                    sql.append(cteSql.sql());
                    parameters.addAll(cteSql.parameters());
                } else if (cte.query() instanceof SqlFragment sf) {
                    sql.append(sf.sql());
                    parameters.addAll(sf.parameters());
                } else {
                    sql.append(cte.query().toString());
                }
                sql.append(")");
            }
            sql.append(" ");
        }

        sql.append("SELECT ");

        // 1. Projection columns
        List<ProjectedColumn> columns = query.columns();
        if (columns.isEmpty()) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                ProjectedColumn col = columns.get(i);
                String renderedExpr = renderExpression(col.expression());
                sql.append(renderedExpr);
                if (col.alias() != null && !col.alias().isBlank()
                        && !col.alias().equalsIgnoreCase(col.expression())
                        && !col.expression().endsWith("." + col.alias())) {
                    sql.append(" AS ").append(dialect.quoteIdentifier(col.alias()));
                }
            }
        }

        // 2. FROM clause
        sql.append(" FROM ");
        renderSourceTable(query.source(), sql, parameters);
        if (query.tableAlias() != null && !query.tableAlias().isBlank()) {
            sql.append(" AS ").append(dialect.quoteIdentifier(query.tableAlias()));
        }

        // 3. JOIN clauses
        for (JoinClause join : query.joins()) {
            sql.append(" ").append(join.joinType().name()).append(" JOIN ");
            renderSourceTable(join.table(), sql, parameters);
            if (join.alias() != null && !join.alias().isBlank()) {
                sql.append(" AS ").append(dialect.quoteIdentifier(join.alias()));
            }
            if (join.condition() != null) {
                BoundSql onSql = WhereClauseBuilder.build(join.condition(), dialect);
                if (!onSql.sql().isBlank()) {
                    sql.append(" ON ").append(onSql.sql());
                    parameters.addAll(onSql.parameters());
                }
            }
        }

        // Keyset Cursor Pagination resolution
        WherePredicate effectiveWhere = query.where();
        List<OrderByClause> effectiveOrderBy = query.orderBy();
        Integer effectiveLimit = query.limit();
        Integer effectiveOffset = query.offset();

        if (query.hasCursorPagination()) {
            CursorPagination cp = query.cursorPagination();
            // Ensure deterministic tie-breaker ordering on keyset pagination
            effectiveOrderBy = KeysetPredicateGenerator.ensureTieBreaker(effectiveOrderBy, "id");

            if (cp.hasAfter() || cp.hasBefore()) {
                boolean isForward = cp.isForward();
                String cursor = cp.hasAfter() ? cp.after() : cp.before();
                List<Object> cursorValues = CursorCodec.decode(cursor);
                WherePredicate keysetPred = KeysetPredicateGenerator.generatePredicate(effectiveOrderBy, cursorValues, isForward);
                effectiveWhere = effectiveWhere == null ? keysetPred : effectiveWhere.and(keysetPred);
            }

            if (cp.limit() != null) {
                effectiveLimit = cp.limit();
                effectiveOffset = null; // keyset pagination avoids offset
            }
        }

        // 4. WHERE clause
        if (effectiveWhere != null) {
            BoundSql whereSql = WhereClauseBuilder.buildWhereClause(effectiveWhere, dialect);
            sql.append(whereSql.sql());
            parameters.addAll(whereSql.parameters());
        }

        // 5. GROUP BY clause
        if (!query.groupBy().isEmpty()) {
            sql.append(" GROUP BY ");
            List<GroupByClause> groupByList = query.groupBy();
            for (int i = 0; i < groupByList.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(renderExpression(groupByList.get(i).expression()));
            }
        }

        // 6. HAVING clause
        if (query.having() != null && query.having().predicate() != null) {
            BoundSql havingSql = WhereClauseBuilder.build(query.having().predicate(), dialect);
            if (!havingSql.sql().isBlank()) {
                sql.append(" HAVING ").append(havingSql.sql());
                parameters.addAll(havingSql.parameters());
            }
        }

        // 7. ORDER BY clause
        if (!effectiveOrderBy.isEmpty()) {
            sql.append(OrderByClauseBuilder.buildOrderByClause(effectiveOrderBy, dialect));
        }

        // 8. LIMIT and OFFSET
        if (effectiveLimit != null || effectiveOffset != null) {
            String limitOffsetClause = dialect.formatLimitOffset(effectiveLimit, effectiveOffset);
            if (!limitOffsetClause.isBlank()) {
                sql.append(" ").append(limitOffsetClause);
                if (effectiveLimit != null) {
                    parameters.add(effectiveLimit);
                }
                if (effectiveOffset != null) {
                    parameters.add(effectiveOffset);
                }
            }
        }

        return new BoundSql(sql.toString(), parameters);
    }

    private void renderSourceTable(SourceTable source, StringBuilder sql, List<Object> parameters) {
        if (source instanceof SourceTable.PhysicalTable physical) {
            sql.append(dialect.quoteIdentifier(physical.tableName()));
        } else if (source instanceof SourceTable.DatabaseView view) {
            sql.append(dialect.quoteIdentifier(view.viewName()));
        } else if (source instanceof SourceTable.SubquerySource subquery) {
            BoundSql subSql = build(subquery.subquery());
            sql.append("(").append(subSql.sql()).append(")");
            parameters.addAll(subSql.parameters());
        } else if (source instanceof SourceTable.RawSqlSource raw) {
            sql.append("(").append(raw.sql().trim()).append(")");
        } else {
            sql.append(dialect.quoteIdentifier(source.identifier()));
        }
    }

    private String renderExpression(String expression) {
        if (expression.equals("*") || expression.contains("(") || expression.contains(" ") || expression.startsWith("\"")) {
            return expression;
        }
        return dialect.quoteIdentifier(expression);
    }
}
