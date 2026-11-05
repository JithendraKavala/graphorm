package com.graphorm.core.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WherePredicate Composable Tree Unit Tests (TASK-5.1)")
class WherePredicateTest {

    @Test
    @DisplayName("Should create all binary comparison predicates")
    void shouldCreateAllBinaryPredicates() {
        WherePredicate eq = WherePredicate.eq("name", "Alice");
        WherePredicate neq = WherePredicate.neq("status", "DELETED");
        WherePredicate gt = WherePredicate.gt("age", 18);
        WherePredicate gte = WherePredicate.gte("gpa", 3.5);
        WherePredicate lt = WherePredicate.lt("balance", 100.0);
        WherePredicate lte = WherePredicate.lte("credits", 60);
        WherePredicate like = WherePredicate.like("email", "%@domain.com");
        WherePredicate ilike = WherePredicate.ilike("title", "java%");

        assertThat(eq).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate bpEq = (WherePredicate.BinaryPredicate) eq;
        assertThat(bpEq.column()).isEqualTo("name");
        assertThat(bpEq.operator()).isEqualTo(ComparisonOperator.EQUALS);
        assertThat(bpEq.value()).isEqualTo("Alice");

        assertThat(((WherePredicate.BinaryPredicate) neq).operator()).isEqualTo(ComparisonOperator.NOT_EQUALS);
        assertThat(((WherePredicate.BinaryPredicate) gt).operator()).isEqualTo(ComparisonOperator.GREATER_THAN);
        assertThat(((WherePredicate.BinaryPredicate) gte).operator()).isEqualTo(ComparisonOperator.GREATER_THAN_OR_EQUAL);
        assertThat(((WherePredicate.BinaryPredicate) lt).operator()).isEqualTo(ComparisonOperator.LESS_THAN);
        assertThat(((WherePredicate.BinaryPredicate) lte).operator()).isEqualTo(ComparisonOperator.LESS_THAN_OR_EQUAL);
        assertThat(((WherePredicate.BinaryPredicate) like).operator()).isEqualTo(ComparisonOperator.LIKE);
        assertThat(((WherePredicate.BinaryPredicate) ilike).operator()).isEqualTo(ComparisonOperator.ILIKE);
    }

    @Test
    @DisplayName("Should create string helper predicates (contains, startsWith, endsWith)")
    void shouldCreateStringHelperPredicates() {
        WherePredicate contains = WherePredicate.contains("bio", "engineer");
        assertThat(contains).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate bpContains = (WherePredicate.BinaryPredicate) contains;
        assertThat(bpContains.operator()).isEqualTo(ComparisonOperator.LIKE);
        assertThat(bpContains.value()).isEqualTo("%engineer%");

        WherePredicate startsWith = WherePredicate.startsWith("name", "Dr.");
        WherePredicate.BinaryPredicate bpStarts = (WherePredicate.BinaryPredicate) startsWith;
        assertThat(bpStarts.operator()).isEqualTo(ComparisonOperator.LIKE);
        assertThat(bpStarts.value()).isEqualTo("Dr.%");

        WherePredicate endsWith = WherePredicate.endsWith("email", ".edu");
        WherePredicate.BinaryPredicate bpEnds = (WherePredicate.BinaryPredicate) endsWith;
        assertThat(bpEnds.operator()).isEqualTo(ComparisonOperator.LIKE);
        assertThat(bpEnds.value()).isEqualTo("%.edu");

        // Case-insensitive variants
        WherePredicate iContains = WherePredicate.iContains("name", "smith");
        WherePredicate.BinaryPredicate bpIContains = (WherePredicate.BinaryPredicate) iContains;
        assertThat(bpIContains.operator()).isEqualTo(ComparisonOperator.ILIKE);
        assertThat(bpIContains.value()).isEqualTo("%smith%");

        WherePredicate iStartsWith = WherePredicate.iStartsWith("code", "cs");
        WherePredicate.BinaryPredicate bpIStarts = (WherePredicate.BinaryPredicate) iStartsWith;
        assertThat(bpIStarts.operator()).isEqualTo(ComparisonOperator.ILIKE);
        assertThat(bpIStarts.value()).isEqualTo("cs%");

        WherePredicate iEndsWith = WherePredicate.iEndsWith("file", ".pdf");
        WherePredicate.BinaryPredicate bpIEnds = (WherePredicate.BinaryPredicate) iEndsWith;
        assertThat(bpIEnds.operator()).isEqualTo(ComparisonOperator.ILIKE);
        assertThat(bpIEnds.value()).isEqualTo("%.pdf");

        // Aliases
        assertThat(WherePredicate.containsIgnoreCase("a", "b")).isEqualTo(WherePredicate.iContains("a", "b"));
        assertThat(WherePredicate.startsWithIgnoreCase("a", "b")).isEqualTo(WherePredicate.iStartsWith("a", "b"));
        assertThat(WherePredicate.endsWithIgnoreCase("a", "b")).isEqualTo(WherePredicate.iEndsWith("a", "b"));

        // notLike and notILike
        assertThat(WherePredicate.notLike("a", "%b%")).isEqualTo(WherePredicate.not(WherePredicate.like("a", "%b%")));
        assertThat(WherePredicate.notILike("a", "%b%")).isEqualTo(WherePredicate.not(WherePredicate.ilike("a", "%b%")));
    }

    @Test
    @DisplayName("Should create IN and NOT IN predicates with list and varargs")
    void shouldCreateInPredicates() {
        WherePredicate inVarargs = WherePredicate.in("status", "ACTIVE", "PENDING", "COMPLETED");
        assertThat(inVarargs).isInstanceOf(WherePredicate.InPredicate.class);
        WherePredicate.InPredicate in = (WherePredicate.InPredicate) inVarargs;
        assertThat(in.column()).isEqualTo("status");
        assertThat(in.isNotIn()).isFalse();
        assertThat(in.values()).containsExactly("ACTIVE", "PENDING", "COMPLETED");

        WherePredicate notInList = WherePredicate.notIn("id", List.of(1, 2, 3));
        assertThat(notInList).isInstanceOf(WherePredicate.InPredicate.class);
        WherePredicate.InPredicate notIn = (WherePredicate.InPredicate) notInList;
        assertThat(notIn.isNotIn()).isTrue();
        assertThat(notIn.values()).containsExactly(1, 2, 3);

        // Empty IN list is supported
        WherePredicate emptyIn = WherePredicate.in("status", List.of());
        assertThat(((WherePredicate.InPredicate) emptyIn).values()).isEmpty();

        // Immutability defense
        List<Object> mutable = new ArrayList<>(List.of("X", "Y"));
        WherePredicate inDefensive = WherePredicate.in("col", mutable);
        mutable.add("Z");
        assertThat(((WherePredicate.InPredicate) inDefensive).values()).containsExactly("X", "Y");
        assertThatThrownBy(() -> ((WherePredicate.InPredicate) inDefensive).values().add("FAIL"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("Should create IS NULL and IS NOT NULL predicates")
    void shouldCreateIsNullPredicates() {
        WherePredicate isNull = WherePredicate.isNull("deleted_at");
        assertThat(isNull).isInstanceOf(WherePredicate.IsNullPredicate.class);
        assertThat(((WherePredicate.IsNullPredicate) isNull).isNull()).isTrue();
        assertThat(((WherePredicate.IsNullPredicate) isNull).column()).isEqualTo("deleted_at");

        WherePredicate isNotNull = WherePredicate.isNotNull("email");
        assertThat(isNotNull).isInstanceOf(WherePredicate.IsNullPredicate.class);
        assertThat(((WherePredicate.IsNullPredicate) isNotNull).isNull()).isFalse();
    }

    @Test
    @DisplayName("Should create BETWEEN and NOT BETWEEN predicates")
    void shouldCreateBetweenPredicates() {
        WherePredicate between = WherePredicate.between("age", 18, 65);
        assertThat(between).isInstanceOf(WherePredicate.BetweenPredicate.class);
        WherePredicate.BetweenPredicate bp = (WherePredicate.BetweenPredicate) between;
        assertThat(bp.column()).isEqualTo("age");
        assertThat(bp.lower()).isEqualTo(18);
        assertThat(bp.upper()).isEqualTo(65);

        WherePredicate notBetween = WherePredicate.notBetween("score", 0, 50);
        assertThat(notBetween).isInstanceOf(WherePredicate.NotPredicate.class);
        assertThat(((WherePredicate.NotPredicate) notBetween).predicate()).isEqualTo(WherePredicate.between("score", 0, 50));
    }

    @Test
    @DisplayName("Should compose AND, OR, and NOT trees with flattening")
    void shouldComposeLogicalTrees() {
        WherePredicate p1 = WherePredicate.gte("age", 18);
        WherePredicate p2 = WherePredicate.startsWith("name", "A");
        WherePredicate p3 = WherePredicate.eq("status", "ACTIVE");

        // Fluent chaining: p1.and(p2.or(p3))
        WherePredicate tree1 = p1.and(p2.or(p3));
        assertThat(tree1).isInstanceOf(WherePredicate.AndPredicate.class);
        WherePredicate.AndPredicate andP = (WherePredicate.AndPredicate) tree1;
        assertThat(andP.predicates()).hasSize(2);
        assertThat(andP.predicates().get(0)).isEqualTo(p1);
        assertThat(andP.predicates().get(1)).isInstanceOf(WherePredicate.OrPredicate.class);

        // Multi-condition AND with flattening
        WherePredicate multiAnd = WherePredicate.and(p1, p2, p3);
        assertThat(multiAnd).isInstanceOf(WherePredicate.AndPredicate.class);
        assertThat(((WherePredicate.AndPredicate) multiAnd).predicates()).containsExactly(p1, p2, p3);

        // Nested AND is flattened
        WherePredicate nested = WherePredicate.and(multiAnd, WherePredicate.eq("active", true));
        assertThat(((WherePredicate.AndPredicate) nested).predicates()).hasSize(4);

        // Multi-condition OR with flattening
        WherePredicate multiOr = WherePredicate.or(p1, p2, p3);
        assertThat(multiOr).isInstanceOf(WherePredicate.OrPredicate.class);
        assertThat(((WherePredicate.OrPredicate) multiOr).predicates()).containsExactly(p1, p2, p3);

        WherePredicate nestedOr = WherePredicate.or(multiOr, WherePredicate.eq("active", false));
        assertThat(((WherePredicate.OrPredicate) nestedOr).predicates()).hasSize(4);

        // Double negation unwrapping
        WherePredicate negated = p1.negate();
        assertThat(negated).isInstanceOf(WherePredicate.NotPredicate.class);
        assertThat(negated.negate()).isEqualTo(p1);

        // Single element in and/or simplifies to the element itself
        WherePredicate singleAnd = WherePredicate.and(List.of(p1));
        assertThat(singleAnd).isEqualTo(p1);

        WherePredicate singleOr = WherePredicate.or(List.of(p1));
        assertThat(singleOr).isEqualTo(p1);
    }

    @Test
    @DisplayName("Should create RawPredicate with parameters")
    void shouldCreateRawPredicate() {
        WherePredicate raw = WherePredicate.raw("MOD(id, 2) = ? AND EXTRACT(YEAR FROM created_at) = ?", 0, 2024);
        assertThat(raw).isInstanceOf(WherePredicate.RawPredicate.class);
        WherePredicate.RawPredicate rp = (WherePredicate.RawPredicate) raw;
        assertThat(rp.sql()).isEqualTo("MOD(id, 2) = ? AND EXTRACT(YEAR FROM created_at) = ?");
        assertThat(rp.parameters()).containsExactly(0, 2024);

        // Empty parameters raw
        WherePredicate rawNoParams = WherePredicate.raw("is_active IS TRUE");
        assertThat(((WherePredicate.RawPredicate) rawNoParams).parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should enforce null and blank validations on factory methods")
    void shouldEnforceValidations() {
        assertThatThrownBy(() -> WherePredicate.eq(null, "val"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.eq("", "val"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WherePredicate.eq("   ", "val"))
                .isInstanceOf(IllegalArgumentException.class);

        // Non-null required on comparison operators
        assertThatThrownBy(() -> WherePredicate.gt("age", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.gte("age", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.lt("age", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.lte("age", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.like("name", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.ilike("name", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.contains("name", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.startsWith("name", null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.endsWith("name", null))
                .isInstanceOf(NullPointerException.class);

        // Between bounds cannot be null
        assertThatThrownBy(() -> WherePredicate.between("age", null, 30))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.between("age", 18, null))
                .isInstanceOf(NullPointerException.class);

        // Combinators cannot take null
        assertThatThrownBy(() -> WherePredicate.not(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.and((WherePredicate[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.and(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WherePredicate.or((WherePredicate[]) null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WherePredicate.or(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
