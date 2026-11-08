package com.graphorm.core.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Strongly typed, composable AST representation of SQL WHERE and HAVING predicates.
 * All implementations are strictly immutable.
 */
public sealed interface WherePredicate permits
        WherePredicate.BinaryPredicate,
        WherePredicate.ColumnComparisonPredicate,
        WherePredicate.RowComparisonPredicate,
        WherePredicate.AndPredicate,
        WherePredicate.OrPredicate,
        WherePredicate.NotPredicate,
        WherePredicate.InPredicate,
        WherePredicate.IsNullPredicate,
        WherePredicate.BetweenPredicate,
        WherePredicate.RawPredicate {

    /**
     * Combines this predicate with another using a logical AND.
     *
     * @param other the other predicate
     * @return a new combined {@link AndPredicate} or simplified predicate
     */
    default WherePredicate and(WherePredicate other) {
        Objects.requireNonNull(other, "Predicate to AND must not be null");
        return WherePredicate.and(this, other);
    }

    /**
     * Combines this predicate with another using a logical OR.
     *
     * @param other the other predicate
     * @return a new combined {@link OrPredicate} or simplified predicate
     */
    default WherePredicate or(WherePredicate other) {
        Objects.requireNonNull(other, "Predicate to OR must not be null");
        return WherePredicate.or(this, other);
    }

    /**
     * Negates this predicate using a logical NOT.
     *
     * @return a new {@link NotPredicate} (or the unwrapped inner predicate if already negated)
     */
    default WherePredicate negate() {
        return WherePredicate.not(this);
    }

    // --- Concrete AST Node Records ---

    /**
     * Binary comparison predicate between a column/expression and a value.
     */
    record BinaryPredicate(String column, ComparisonOperator operator, Object value) implements WherePredicate {
        public BinaryPredicate {
            Objects.requireNonNull(column, "Column must not be null");
            if (column.isBlank()) {
                throw new IllegalArgumentException("Column must not be blank");
            }
            Objects.requireNonNull(operator, "Operator must not be null");
        }
    }

    /**
     * Comparison predicate between two columns or expressions (e.g. for join conditions: leftCol = rightCol).
     */
    record ColumnComparisonPredicate(String leftColumn, ComparisonOperator operator, String rightColumn) implements WherePredicate {
        public ColumnComparisonPredicate {
            Objects.requireNonNull(leftColumn, "Left column must not be null");
            if (leftColumn.isBlank()) {
                throw new IllegalArgumentException("Left column must not be blank");
            }
            Objects.requireNonNull(operator, "Operator must not be null");
            Objects.requireNonNull(rightColumn, "Right column must not be null");
            if (rightColumn.isBlank()) {
                throw new IllegalArgumentException("Right column must not be blank");
            }
        }
    }

    /**
     * Row constructor comparison predicate (e.g. for keyset pagination: (created_at, id) > (?, ?)).
     */
    record RowComparisonPredicate(List<String> columns, ComparisonOperator operator, List<Object> values) implements WherePredicate {
        public RowComparisonPredicate {
            Objects.requireNonNull(columns, "Columns must not be null");
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("RowComparisonPredicate requires at least one column");
            }
            Objects.requireNonNull(operator, "Operator must not be null");
            Objects.requireNonNull(values, "Values must not be null");
            if (columns.size() != values.size()) {
                throw new IllegalArgumentException("Column count (" + columns.size() + ") must match value count (" + values.size() + ")");
            }
            columns = List.copyOf(columns);
            values = List.copyOf(values);
        }
    }

    /**
     * Conjunction (AND) of multiple child predicates.
     */
    record AndPredicate(List<WherePredicate> predicates) implements WherePredicate {
        public AndPredicate {
            Objects.requireNonNull(predicates, "Predicates list must not be null");
            if (predicates.isEmpty()) {
                throw new IllegalArgumentException("AndPredicate must contain at least one predicate");
            }
            predicates = List.copyOf(predicates);
        }
    }

    /**
     * Disjunction (OR) of multiple child predicates.
     */
    record OrPredicate(List<WherePredicate> predicates) implements WherePredicate {
        public OrPredicate {
            Objects.requireNonNull(predicates, "Predicates list must not be null");
            if (predicates.isEmpty()) {
                throw new IllegalArgumentException("OrPredicate must contain at least one predicate");
            }
            predicates = List.copyOf(predicates);
        }
    }

    /**
     * Logical negation (NOT) of a child predicate.
     */
    record NotPredicate(WherePredicate predicate) implements WherePredicate {
        public NotPredicate {
            Objects.requireNonNull(predicate, "Predicate to negate must not be null");
        }
    }

    /**
     * IN or NOT IN predicate for a column against a collection of values.
     */
    record InPredicate(String column, List<Object> values, boolean isNotIn) implements WherePredicate {
        public InPredicate {
            Objects.requireNonNull(column, "Column must not be null");
            if (column.isBlank()) {
                throw new IllegalArgumentException("Column must not be blank");
            }
            Objects.requireNonNull(values, "Values must not be null");
            values = Collections.unmodifiableList(new ArrayList<>(values));
        }
    }

    /**
     * IS NULL or IS NOT NULL predicate for a column.
     */
    record IsNullPredicate(String column, boolean isNull) implements WherePredicate {
        public IsNullPredicate {
            Objects.requireNonNull(column, "Column must not be null");
            if (column.isBlank()) {
                throw new IllegalArgumentException("Column must not be blank");
            }
        }
    }

    /**
     * BETWEEN lower AND upper predicate for a column.
     */
    record BetweenPredicate(String column, Object lower, Object upper) implements WherePredicate {
        public BetweenPredicate {
            Objects.requireNonNull(column, "Column must not be null");
            if (column.isBlank()) {
                throw new IllegalArgumentException("Column must not be blank");
            }
            Objects.requireNonNull(lower, "Lower bound must not be null");
            Objects.requireNonNull(upper, "Upper bound must not be null");
        }
    }

    /**
     * Parameterized raw SQL predicate snippet with positional parameters.
     *
     * @deprecated As of 0.1.0, prefer strongly typed AST predicates ({@link #eq}, {@link #and}, {@link #between}, etc.).
     *             RawPredicate bypasses AST sanitization and identifier quoting. It is intended strictly for internal
     *             dialect extensions or complex vendor-specific expressions with parameterized inputs.
     *             <p>
     *             <strong>SECURITY WARNING (SEC-05):</strong> Never construct {@code RawPredicate} by concatenating
     *             untrusted user input. Always parameterize arguments using positional '?' placeholders.\n     */
    @Deprecated(since = "0.1.0")
    record RawPredicate(String sql, List<Object> parameters) implements WherePredicate {
        public RawPredicate {
            Objects.requireNonNull(sql, "SQL must not be null");
            if (sql.isBlank()) {
                throw new IllegalArgumentException("SQL must not be blank");
            }
            parameters = parameters == null ? List.of() : List.copyOf(parameters);
        }
    }

    // --- Static Factory Methods ---

    static WherePredicate eq(String column, Object value) {
        return new BinaryPredicate(column, ComparisonOperator.EQUALS, value);
    }

    static WherePredicate neq(String column, Object value) {
        return new BinaryPredicate(column, ComparisonOperator.NOT_EQUALS, value);
    }

    static WherePredicate gt(String column, Object value) {
        Objects.requireNonNull(value, "Value for gt must not be null");
        return new BinaryPredicate(column, ComparisonOperator.GREATER_THAN, value);
    }

    static WherePredicate gte(String column, Object value) {
        Objects.requireNonNull(value, "Value for gte must not be null");
        return new BinaryPredicate(column, ComparisonOperator.GREATER_THAN_OR_EQUAL, value);
    }

    static WherePredicate lt(String column, Object value) {
        Objects.requireNonNull(value, "Value for lt must not be null");
        return new BinaryPredicate(column, ComparisonOperator.LESS_THAN, value);
    }

    static WherePredicate lte(String column, Object value) {
        Objects.requireNonNull(value, "Value for lte must not be null");
        return new BinaryPredicate(column, ComparisonOperator.LESS_THAN_OR_EQUAL, value);
    }

    static WherePredicate like(String column, String pattern) {
        Objects.requireNonNull(pattern, "Pattern for like must not be null");
        return new BinaryPredicate(column, ComparisonOperator.LIKE, pattern);
    }

    static WherePredicate ilike(String column, String pattern) {
        Objects.requireNonNull(pattern, "Pattern for ilike must not be null");
        return new BinaryPredicate(column, ComparisonOperator.ILIKE, pattern);
    }

    static WherePredicate notLike(String column, String pattern) {
        return not(like(column, pattern));
    }

    static WherePredicate notILike(String column, String pattern) {
        return not(ilike(column, pattern));
    }

    static WherePredicate contains(String column, String substring) {
        Objects.requireNonNull(substring, "Substring for contains must not be null");
        return like(column, "%" + substring + "%");
    }

    static WherePredicate startsWith(String column, String prefix) {
        Objects.requireNonNull(prefix, "Prefix for startsWith must not be null");
        return like(column, prefix + "%");
    }

    static WherePredicate endsWith(String column, String suffix) {
        Objects.requireNonNull(suffix, "Suffix for endsWith must not be null");
        return like(column, "%" + suffix);
    }

    static WherePredicate iContains(String column, String substring) {
        Objects.requireNonNull(substring, "Substring for iContains must not be null");
        return ilike(column, "%" + substring + "%");
    }

    static WherePredicate iStartsWith(String column, String prefix) {
        Objects.requireNonNull(prefix, "Prefix for iStartsWith must not be null");
        return ilike(column, prefix + "%");
    }

    static WherePredicate iEndsWith(String column, String suffix) {
        Objects.requireNonNull(suffix, "Suffix for iEndsWith must not be null");
        return ilike(column, "%" + suffix);
    }

    static WherePredicate containsIgnoreCase(String column, String substring) {
        return iContains(column, substring);
    }

    static WherePredicate startsWithIgnoreCase(String column, String prefix) {
        return iStartsWith(column, prefix);
    }

    static WherePredicate endsWithIgnoreCase(String column, String suffix) {
        return iEndsWith(column, suffix);
    }

    static WherePredicate in(String column, Collection<?> values) {
        Objects.requireNonNull(values, "Values must not be null");
        return new InPredicate(column, new ArrayList<>(values), false);
    }

    static WherePredicate in(String column, Object... values) {
        Objects.requireNonNull(values, "Values must not be null");
        return new InPredicate(column, Arrays.asList(values), false);
    }

    static WherePredicate notIn(String column, Collection<?> values) {
        Objects.requireNonNull(values, "Values must not be null");
        return new InPredicate(column, new ArrayList<>(values), true);
    }

    static WherePredicate notIn(String column, Object... values) {
        Objects.requireNonNull(values, "Values must not be null");
        return new InPredicate(column, Arrays.asList(values), true);
    }

    static WherePredicate isNull(String column) {
        return new IsNullPredicate(column, true);
    }

    static WherePredicate isNotNull(String column) {
        return new IsNullPredicate(column, false);
    }

    static WherePredicate between(String column, Object lower, Object upper) {
        return new BetweenPredicate(column, lower, upper);
    }

    static WherePredicate notBetween(String column, Object lower, Object upper) {
        return not(between(column, lower, upper));
    }

    static WherePredicate columnEq(String leftColumn, String rightColumn) {
        return new ColumnComparisonPredicate(leftColumn, ComparisonOperator.EQUALS, rightColumn);
    }

    static WherePredicate columnNeq(String leftColumn, String rightColumn) {
        return new ColumnComparisonPredicate(leftColumn, ComparisonOperator.NOT_EQUALS, rightColumn);
    }

    static WherePredicate columnComparison(String leftColumn, ComparisonOperator operator, String rightColumn) {
        return new ColumnComparisonPredicate(leftColumn, operator, rightColumn);
    }

    static WherePredicate rowComparison(List<String> columns, ComparisonOperator operator, List<Object> values) {
        return new RowComparisonPredicate(columns, operator, values);
    }

    static WherePredicate and(WherePredicate... predicates) {
        Objects.requireNonNull(predicates, "Predicates must not be null");
        return and(Arrays.asList(predicates));
    }

    static WherePredicate and(List<WherePredicate> predicates) {
        Objects.requireNonNull(predicates, "Predicates must not be null");
        List<WherePredicate> flattened = new ArrayList<>();
        for (WherePredicate p : predicates) {
            Objects.requireNonNull(p, "Predicate elements must not be null");
            if (p instanceof AndPredicate andP) {
                flattened.addAll(andP.predicates());
            } else {
                flattened.add(p);
            }
        }
        if (flattened.isEmpty()) {
            throw new IllegalArgumentException("AndPredicate must contain at least one predicate");
        }
        if (flattened.size() == 1) {
            return flattened.getFirst();
        }
        return new AndPredicate(flattened);
    }

    static WherePredicate or(WherePredicate... predicates) {
        Objects.requireNonNull(predicates, "Predicates must not be null");
        return or(Arrays.asList(predicates));
    }

    static WherePredicate or(List<WherePredicate> predicates) {
        Objects.requireNonNull(predicates, "Predicates must not be null");
        List<WherePredicate> flattened = new ArrayList<>();
        for (WherePredicate p : predicates) {
            Objects.requireNonNull(p, "Predicate elements must not be null");
            if (p instanceof OrPredicate orP) {
                flattened.addAll(orP.predicates());
            } else {
                flattened.add(p);
            }
        }
        if (flattened.isEmpty()) {
            throw new IllegalArgumentException("OrPredicate must contain at least one predicate");
        }
        if (flattened.size() == 1) {
            return flattened.getFirst();
        }
        return new OrPredicate(flattened);
    }

    static WherePredicate not(WherePredicate predicate) {
        Objects.requireNonNull(predicate, "Predicate to negate must not be null");
        if (predicate instanceof NotPredicate notP) {
            return notP.predicate();
        }
        return new NotPredicate(predicate);
    }

    static WherePredicate raw(String sql, Object... parameters) {
        return new RawPredicate(sql, parameters != null ? Arrays.asList(parameters) : List.of());
    }

    static WherePredicate raw(String sql, List<Object> parameters) {
        return new RawPredicate(sql, parameters);
    }
}
