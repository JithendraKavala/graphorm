package com.graphorm.jdbc.view;

import com.graphorm.core.query.GroupByClause;
import com.graphorm.core.query.HavingPredicate;
import com.graphorm.core.query.JoinClause;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.ProjectedColumn;
import com.graphorm.core.query.WherePredicate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Optimizes view queries by pruning unneeded joins and omitting unnecessary GROUP BY clauses.
 * <p>
 * A join can be safely pruned when:
 * <ol>
 *     <li>None of its columns are selected in the projection.</li>
 *     <li>It is not referenced in the WHERE filter.</li>
 *     <li>It is not referenced in the ORDER BY sorting.</li>
 *     <li>It is not referenced in the HAVING predicate.</li>
 *     <li>No other non-pruned join depends on it in an ON condition.</li>
 * </ol>
 * <p>
 * When all aggregate expressions are unselected and no HAVING clause exists,
 * the GROUP BY clause is also pruned to produce a streamlined SELECT query.
 */
public final class JoinPruner {

    private static final Set<String> AGGREGATE_FUNCTIONS = Set.of(
            "COUNT(", "SUM(", "AVG(", "MIN(", "MAX("
    );

    private JoinPruner() {}

    /**
     * Result of join pruning containing the optimized joins and group-by clauses.
     *
     * @param joins          the retained join clauses
     * @param groupBy        the retained group-by clauses (empty if group by was pruned)
     * @param prunedAliases  the aliases that were pruned
     */
    public record PruningResult(
            List<JoinClause> joins,
            List<GroupByClause> groupBy,
            Set<String> prunedAliases
    ) {
        public PruningResult {
            joins = joins == null ? List.of() : List.copyOf(joins);
            groupBy = groupBy == null ? List.of() : List.copyOf(groupBy);
            prunedAliases = prunedAliases == null ? Set.of() : Set.copyOf(prunedAliases);
        }
    }

    /**
     * Prunes unneeded joins and group-by clauses based on projected columns, filters, sorting, and conditions.
     *
     * @param sourceAlias      the root source table alias (e.g. "s")
     * @param joins            the declared join clauses
     * @param projectedColumns the projected columns in the SELECT clause
     * @param where            the WHERE predicate (may be null)
     * @param having           the HAVING predicate (may be null)
     * @param orderBy          the ORDER BY sorting clauses
     * @param groupBy          the declared GROUP BY clauses
     * @return the pruned result
     */
    public static PruningResult prune(
            String sourceAlias,
            List<JoinClause> joins,
            List<ProjectedColumn> projectedColumns,
            WherePredicate where,
            HavingPredicate having,
            List<OrderByClause> orderBy,
            List<GroupByClause> groupBy
    ) {
        Objects.requireNonNull(joins, "joins must not be null");
        projectedColumns = projectedColumns == null ? List.of() : projectedColumns;
        orderBy = orderBy == null ? List.of() : orderBy;
        groupBy = groupBy == null ? List.of() : groupBy;

        Set<String> allJoinAliases = new LinkedHashSet<>();
        for (JoinClause join : joins) {
            if (join.alias() != null && !join.alias().isBlank()) {
                allJoinAliases.add(join.alias());
            }
        }

        // 1. Collect required aliases
        Set<String> requiredAliases = new HashSet<>();
        if (sourceAlias != null && !sourceAlias.isBlank()) {
            requiredAliases.add(sourceAlias);
        }

        // From projected columns
        for (ProjectedColumn col : projectedColumns) {
            findReferencedAliases(col.expression(), allJoinAliases, requiredAliases);
        }

        // From WHERE
        if (where != null) {
            findAliasesInPredicate(where, allJoinAliases, requiredAliases);
        }

        // From HAVING
        if (having != null && having.predicate() != null) {
            findAliasesInPredicate(having.predicate(), allJoinAliases, requiredAliases);
        }

        // From ORDER BY
        for (OrderByClause ob : orderBy) {
            findReferencedAliases(ob.expression(), allJoinAliases, requiredAliases);
        }

        // Propagate dependencies across join ON conditions (e.g., join C depends on join B)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (JoinClause join : joins) {
                if (requiredAliases.contains(join.alias()) && join.condition() != null) {
                    int beforeSize = requiredAliases.size();
                    findAliasesInPredicate(join.condition(), allJoinAliases, requiredAliases);
                    if (requiredAliases.size() > beforeSize) {
                        changed = true;
                    }
                }
            }
        }

        // 2. Retain only required joins
        List<JoinClause> retainedJoins = new ArrayList<>();
        Set<String> prunedAliases = new LinkedHashSet<>();

        for (JoinClause join : joins) {
            if (join.alias() == null || requiredAliases.contains(join.alias())) {
                retainedJoins.add(join);
            } else {
                prunedAliases.add(join.alias());
            }
        }

        // 3. Determine if GROUP BY should be retained or pruned
        boolean hasAggregates = hasAnyAggregate(projectedColumns)
                || (having != null && having.predicate() != null);

        List<GroupByClause> retainedGroupBy = new ArrayList<>();
        if (hasAggregates) {
            for (GroupByClause gb : groupBy) {
                // Keep only if the expression does NOT reference a pruned alias
                if (!referencesAnyAlias(gb.expression(), prunedAliases)) {
                    retainedGroupBy.add(gb);
                }
            }
        }

        return new PruningResult(retainedJoins, retainedGroupBy, prunedAliases);
    }

    private static boolean hasAnyAggregate(List<ProjectedColumn> columns) {
        for (ProjectedColumn col : columns) {
            String exprUpper = col.expression().toUpperCase();
            for (String func : AGGREGATE_FUNCTIONS) {
                if (exprUpper.contains(func)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void findReferencedAliases(
            String expression,
            Collection<String> candidateAliases,
            Set<String> target
    ) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        for (String alias : candidateAliases) {
            if (referencesAlias(expression, alias)) {
                target.add(alias);
            }
        }
    }

    private static boolean referencesAlias(String expression, String alias) {
        if (expression == null || alias == null) {
            return false;
        }
        // Match alias as a prefix like "s.col" or "\"s\".col" or within expressions
        String escaped = Pattern.quote(alias);
        Pattern pattern = Pattern.compile("(?i)(^|[^a-zA-Z0-9_\"])\"?" + escaped + "\"?\\.");
        return pattern.matcher(expression).find();
    }

    private static boolean referencesAnyAlias(String expression, Collection<String> aliases) {
        for (String alias : aliases) {
            if (referencesAlias(expression, alias)) {
                return true;
            }
        }
        return false;
    }

    private static void findAliasesInPredicate(
            WherePredicate predicate,
            Collection<String> candidateAliases,
            Set<String> target
    ) {
        if (predicate == null) {
            return;
        }
        if (predicate instanceof WherePredicate.BinaryPredicate b) {
            findReferencedAliases(b.column(), candidateAliases, target);
        } else if (predicate instanceof WherePredicate.ColumnComparisonPredicate c) {
            findReferencedAliases(c.leftColumn(), candidateAliases, target);
            findReferencedAliases(c.rightColumn(), candidateAliases, target);
        } else if (predicate instanceof WherePredicate.InPredicate in) {
            findReferencedAliases(in.column(), candidateAliases, target);
        } else if (predicate instanceof WherePredicate.IsNullPredicate isNull) {
            findReferencedAliases(isNull.column(), candidateAliases, target);
        } else if (predicate instanceof WherePredicate.BetweenPredicate between) {
            findReferencedAliases(between.column(), candidateAliases, target);
        } else if (predicate instanceof WherePredicate.AndPredicate and) {
            for (WherePredicate p : and.predicates()) {
                findAliasesInPredicate(p, candidateAliases, target);
            }
        } else if (predicate instanceof WherePredicate.OrPredicate or) {
            for (WherePredicate p : or.predicates()) {
                findAliasesInPredicate(p, candidateAliases, target);
            }
        } else if (predicate instanceof WherePredicate.NotPredicate not) {
            findAliasesInPredicate(not.predicate(), candidateAliases, target);
        } else if (predicate instanceof WherePredicate.RawPredicate raw) {
            findReferencedAliases(raw.sql(), candidateAliases, target);
        }
    }
}
