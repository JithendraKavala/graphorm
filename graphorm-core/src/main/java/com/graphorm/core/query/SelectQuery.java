package com.graphorm.core.query;

import com.graphorm.core.pagination.CursorPagination;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Strongly typed, immutable AST representation of a unified SQL SELECT query.
 * Represents queries for both regular physical models and schema-defined view models.
 *
 * @param source           the driving source relation (table, view, or subquery)
 * @param tableAlias       the alias for the source relation (optional)
 * @param columns          the projected column expressions
 * @param joins            the list of joined relations
 * @param where            the WHERE predicate (optional)
 * @param groupBy          the GROUP BY expressions
 * @param having           the HAVING predicate (optional)
 * @param orderBy          the ORDER BY sorting criteria
 * @param limit            the maximum rows to return (optional)
 * @param offset           the number of rows to skip (optional)
 * @param ctes             Common Table Expressions (WITH ... AS (...))
 * @param cursorPagination keyset cursor pagination specification (optional)
 */
public record SelectQuery(
        SourceTable source,
        String tableAlias,
        List<ProjectedColumn> columns,
        List<JoinClause> joins,
        WherePredicate where,
        List<GroupByClause> groupBy,
        HavingPredicate having,
        List<OrderByClause> orderBy,
        Integer limit,
        Integer offset,
        List<CommonTableExpression> ctes,
        CursorPagination cursorPagination
) implements SqlQuery {

    public SelectQuery {
        Objects.requireNonNull(source, "Source table must not be null");
        columns = columns == null ? List.of() : List.copyOf(columns);
        joins = joins == null ? List.of() : List.copyOf(joins);
        groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
        orderBy = orderBy == null ? List.of() : List.copyOf(orderBy);
        ctes = ctes == null ? List.of() : List.copyOf(ctes);

        if (limit != null && limit < 0) {
            throw new IllegalArgumentException("Limit cannot be negative: " + limit);
        }
        if (offset != null && offset < 0) {
            throw new IllegalArgumentException("Offset cannot be negative: " + offset);
        }
    }

    /**
     * Backward-compatible constructor without ctes and cursorPagination.
     */
    public SelectQuery(
            SourceTable source,
            String tableAlias,
            List<ProjectedColumn> columns,
            List<JoinClause> joins,
            WherePredicate where,
            List<GroupByClause> groupBy,
            HavingPredicate having,
            List<OrderByClause> orderBy,
            Integer limit,
            Integer offset
    ) {
        this(source, tableAlias, columns, joins, where, groupBy, having, orderBy, limit, offset, List.of(), null);
    }

    @Override
    public QueryType queryType() {
        return QueryType.SELECT;
    }

    public boolean hasCtes() {
        return !ctes.isEmpty();
    }

    public boolean hasCursorPagination() {
        return cursorPagination != null;
    }

    public boolean hasGroupBy() {
        return !groupBy.isEmpty();
    }

    public boolean hasHaving() {
        return having != null;
    }

    public boolean hasJoins() {
        return !joins.isEmpty();
    }

    public boolean hasWhere() {
        return where != null;
    }

    /**
     * Creates a builder initialized with the given source table.
     *
     * @param source the source table
     * @return a new Builder instance
     */
    public static Builder from(SourceTable source) {
        return new Builder(source);
    }

    /**
     * Creates a builder initialized with a physical table source.
     *
     * @param tableName the physical table name
     * @return a new Builder instance
     */
    public static Builder from(String tableName) {
        return new Builder(SourceTable.table(tableName));
    }

    /**
     * Creates a builder initialized with a database view source.
     *
     * @param viewName the database view name
     * @return a new Builder instance
     */
    public static Builder fromView(String viewName) {
        return new Builder(SourceTable.view(viewName));
    }

    /**
     * Fluent builder for {@link SelectQuery}.
     */
    public static final class Builder {
        private final SourceTable source;
        private String tableAlias;
        private final List<ProjectedColumn> columns = new ArrayList<>();
        private final List<JoinClause> joins = new ArrayList<>();
        private WherePredicate where;
        private WherePredicate tenantWhere;
        private final List<GroupByClause> groupBy = new ArrayList<>();
        private HavingPredicate having;
        private final List<OrderByClause> orderBy = new ArrayList<>();
        private Integer limit;
        private Integer offset;
        private final List<CommonTableExpression> ctes = new ArrayList<>();
        private CursorPagination cursorPagination;

        private Builder(SourceTable source) {
            this.source = Objects.requireNonNull(source, "Source table must not be null");
        }

        public Builder as(String tableAlias) {
            this.tableAlias = tableAlias;
            return this;
        }

        public Builder with(CommonTableExpression cte) {
            this.ctes.add(Objects.requireNonNull(cte, "CTE must not be null"));
            return this;
        }

        public Builder with(String name, SqlQuery query) {
            return with(CommonTableExpression.of(name, query));
        }

        public Builder with(String name, List<String> columnAliases, SqlQuery query) {
            return with(CommonTableExpression.of(name, columnAliases, query));
        }

        public Builder withRecursive(String name, SqlQuery query) {
            return with(CommonTableExpression.recursive(name, query));
        }

        public Builder withRecursive(String name, List<String> columnAliases, SqlQuery query) {
            return with(CommonTableExpression.recursive(name, columnAliases, query));
        }

        public Builder ctes(List<CommonTableExpression> ctes) {
            this.ctes.clear();
            if (ctes != null) {
                this.ctes.addAll(ctes);
            }
            return this;
        }

        public Builder cursorPagination(CursorPagination cursorPagination) {
            this.cursorPagination = cursorPagination;
            return this;
        }

        public Builder after(String cursor) {
            this.cursorPagination = new CursorPagination(cursor, null, this.limit, null);
            return this;
        }

        public Builder before(String cursor) {
            this.cursorPagination = new CursorPagination(null, cursor, null, this.limit);
            return this;
        }

        public Builder first(Integer first) {
            this.limit = first;
            if (this.cursorPagination != null) {
                this.cursorPagination = new CursorPagination(this.cursorPagination.after(), null, first, null);
            } else {
                this.cursorPagination = new CursorPagination(null, null, first, null);
            }
            return this;
        }

        public Builder last(Integer last) {
            this.limit = last;
            if (this.cursorPagination != null) {
                this.cursorPagination = new CursorPagination(null, this.cursorPagination.before(), null, last);
            } else {
                this.cursorPagination = new CursorPagination(null, null, null, last);
            }
            return this;
        }

        public Builder column(ProjectedColumn column) {
            this.columns.add(Objects.requireNonNull(column, "Column must not be null"));
            return this;
        }

        public Builder column(String expression) {
            return column(ProjectedColumn.of(expression));
        }

        public Builder column(String expression, String alias) {
            return column(ProjectedColumn.of(expression, alias));
        }

        public Builder hiddenColumn(String expression, String alias) {
            return column(ProjectedColumn.hidden(expression, alias));
        }

        public Builder columns(List<ProjectedColumn> columns) {
            this.columns.clear();
            if (columns != null) {
                this.columns.addAll(columns);
            }
            return this;
        }

        public Builder select(String... expressions) {
            return columns(expressions);
        }

        public Builder columns(String... expressions) {
            if (expressions != null) {
                for (String expr : expressions) {
                    column(expr);
                }
            }
            return this;
        }

        public Builder join(JoinClause join) {
            this.joins.add(Objects.requireNonNull(join, "Join must not be null"));
            return this;
        }

        public Builder joins(List<JoinClause> joins) {
            this.joins.clear();
            if (joins != null) {
                this.joins.addAll(joins);
            }
            return this;
        }

        public Builder where(WherePredicate where) {
            this.where = where;
            return this;
        }

        public Builder tenantWhere(WherePredicate tenantWhere) {
            this.tenantWhere = tenantWhere;
            return this;
        }

        public WherePredicate tenantWhere() {
            return this.tenantWhere;
        }

        public Builder andWhere(WherePredicate predicate) {
            Objects.requireNonNull(predicate, "Predicate must not be null");
            if (this.where == null) {
                this.where = predicate;
            } else {
                this.where = this.where.and(predicate);
            }
            return this;
        }

        public Builder orWhere(WherePredicate predicate) {
            Objects.requireNonNull(predicate, "Predicate must not be null");
            if (this.where == null) {
                this.where = predicate;
            } else {
                this.where = this.where.or(predicate);
            }
            return this;
        }

        public Builder groupBy(GroupByClause groupBy) {
            this.groupBy.add(Objects.requireNonNull(groupBy, "GroupBy must not be null"));
            return this;
        }

        public Builder groupBy(String expression) {
            return groupBy(GroupByClause.of(expression));
        }

        public Builder groupBy(List<GroupByClause> groupBy) {
            this.groupBy.clear();
            if (groupBy != null) {
                this.groupBy.addAll(groupBy);
            }
            return this;
        }

        public Builder groupBy(String... expressions) {
            if (expressions != null) {
                for (String expr : expressions) {
                    groupBy(expr);
                }
            }
            return this;
        }

        public Builder having(HavingPredicate having) {
            this.having = having;
            return this;
        }

        public Builder having(WherePredicate predicate) {
            this.having = predicate != null ? HavingPredicate.of(predicate) : null;
            return this;
        }

        public Builder orderBy(OrderByClause orderBy) {
            this.orderBy.add(Objects.requireNonNull(orderBy, "OrderBy must not be null"));
            return this;
        }

        public Builder orderBy(OrderByClause... orderBy) {
            if (orderBy != null) {
                for (OrderByClause clause : orderBy) {
                    if (clause != null) {
                        this.orderBy.add(clause);
                    }
                }
            }
            return this;
        }

        public Builder orderBy(String expression, OrderDirection direction) {
            return orderBy(new OrderByClause(expression, direction, NullsOrder.DEFAULT));
        }

        public Builder orderBy(String expression, OrderDirection direction, NullsOrder nullsOrder) {
            return orderBy(new OrderByClause(expression, direction, nullsOrder));
        }

        public Builder orderByAsc(String expression) {
            return orderBy(OrderByClause.asc(expression));
        }

        public Builder orderByAsc(String expression, NullsOrder nullsOrder) {
            return orderBy(OrderByClause.asc(expression, nullsOrder));
        }

        public Builder orderByDesc(String expression) {
            return orderBy(OrderByClause.desc(expression));
        }

        public Builder orderByDesc(String expression, NullsOrder nullsOrder) {
            return orderBy(OrderByClause.desc(expression, nullsOrder));
        }

        public Builder orderBy(List<OrderByClause> orderBy) {
            this.orderBy.clear();
            if (orderBy != null) {
                this.orderBy.addAll(orderBy);
            }
            return this;
        }

        public Builder limit(Integer limit) {
            this.limit = limit;
            return this;
        }

        public Builder offset(Integer offset) {
            this.offset = offset;
            return this;
        }

        public SelectQuery build() {
            WherePredicate finalWhere = this.where;
            if (this.tenantWhere != null) {
                finalWhere = finalWhere != null ? this.tenantWhere.and(finalWhere) : this.tenantWhere;
            }
            return new SelectQuery(
                    source,
                    tableAlias,
                    columns,
                    joins,
                    finalWhere,
                    groupBy,
                    having,
                    orderBy,
                    limit,
                    offset,
                    ctes,
                    cursorPagination
            );
        }
    }
}
