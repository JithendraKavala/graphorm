package com.graphorm.jdbc.builder;

import com.graphorm.core.query.WherePredicate;
import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import com.graphorm.jdbc.dialect.SqlDialect;
import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WhereClauseBuilder Parameterized Rendering Unit Tests (TASK-5.2)")
class WhereClauseBuilderTest {

    private SqlDialect dialect;

    @BeforeEach
    void setUp() {
        dialect = new PostgreSqlDialect();
    }

    @Test
    @DisplayName("Should return empty BoundSql when predicate is null")
    void shouldHandleNullPredicate() {
        BoundSql boundSql = WhereClauseBuilder.buildWhereClause(null, dialect);
        assertThat(boundSql.sql()).isEmpty();
        assertThat(boundSql.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should render all comparison operators with bound parameters")
    void shouldRenderAllComparisonOperators() {
        // EQUALS
        BoundSql eq = WhereClauseBuilder.build(WherePredicate.eq("name", "Alice"), dialect);
        assertThat(eq.sql()).isEqualTo("\"name\" = ?");
        assertThat(eq.parameters()).containsExactly("Alice");

        // NOT EQUALS
        BoundSql neq = WhereClauseBuilder.build(WherePredicate.neq("status", "DELETED"), dialect);
        assertThat(neq.sql()).isEqualTo("\"status\" != ?");
        assertThat(neq.parameters()).containsExactly("DELETED");

        // GREATER THAN
        BoundSql gt = WhereClauseBuilder.build(WherePredicate.gt("age", 21), dialect);
        assertThat(gt.sql()).isEqualTo("\"age\" > ?");
        assertThat(gt.parameters()).containsExactly(21);

        // GREATER THAN OR EQUAL
        BoundSql gte = WhereClauseBuilder.build(WherePredicate.gte("gpa", 3.5), dialect);
        assertThat(gte.sql()).isEqualTo("\"gpa\" >= ?");
        assertThat(gte.parameters()).containsExactly(3.5);

        // LESS THAN
        BoundSql lt = WhereClauseBuilder.build(WherePredicate.lt("balance", 0.0), dialect);
        assertThat(lt.sql()).isEqualTo("\"balance\" < ?");
        assertThat(lt.parameters()).containsExactly(0.0);

        // LESS THAN OR EQUAL
        BoundSql lte = WhereClauseBuilder.build(WherePredicate.lte("credits", 120), dialect);
        assertThat(lte.sql()).isEqualTo("\"credits\" <= ?");
        assertThat(lte.parameters()).containsExactly(120);

        // LIKE
        BoundSql like = WhereClauseBuilder.build(WherePredicate.like("email", "%@domain.com"), dialect);
        assertThat(like.sql()).isEqualTo("\"email\" LIKE ?");
        assertThat(like.parameters()).containsExactly("%@domain.com");

        // ILIKE
        BoundSql ilike = WhereClauseBuilder.build(WherePredicate.ilike("title", "java%"), dialect);
        assertThat(ilike.sql()).isEqualTo("\"title\" ILIKE ?");
        assertThat(ilike.parameters()).containsExactly("java%");
    }

    @Test
    @DisplayName("Should render string helper predicates (contains, startsWith, endsWith, iContains)")
    void shouldRenderStringHelperPredicates() {
        BoundSql contains = WhereClauseBuilder.build(WherePredicate.contains("bio", "computer"), dialect);
        assertThat(contains.sql()).isEqualTo("\"bio\" LIKE ?");
        assertThat(contains.parameters()).containsExactly("%computer%");

        BoundSql startsWith = WhereClauseBuilder.build(WherePredicate.startsWith("name", "Dr."), dialect);
        assertThat(startsWith.sql()).isEqualTo("\"name\" LIKE ?");
        assertThat(startsWith.parameters()).containsExactly("Dr.%");

        BoundSql endsWith = WhereClauseBuilder.build(WherePredicate.endsWith("email", ".edu"), dialect);
        assertThat(endsWith.sql()).isEqualTo("\"email\" LIKE ?");
        assertThat(endsWith.parameters()).containsExactly("%.edu");

        BoundSql iContains = WhereClauseBuilder.build(WherePredicate.iContains("name", "hopper"), dialect);
        assertThat(iContains.sql()).isEqualTo("\"name\" ILIKE ?");
        assertThat(iContains.parameters()).containsExactly("%hopper%");

        BoundSql iStartsWith = WhereClauseBuilder.build(WherePredicate.iStartsWith("code", "cs"), dialect);
        assertThat(iStartsWith.sql()).isEqualTo("\"code\" ILIKE ?");
        assertThat(iStartsWith.parameters()).containsExactly("cs%");

        BoundSql iEndsWith = WhereClauseBuilder.build(WherePredicate.iEndsWith("file", ".pdf"), dialect);
        assertThat(iEndsWith.sql()).isEqualTo("\"file\" ILIKE ?");
        assertThat(iEndsWith.parameters()).containsExactly("%.pdf");
    }

    @Test
    @DisplayName("Should convert equals/not-equals null to IS NULL and IS NOT NULL without parameters")
    void shouldHandleNullValuesInBinaryPredicate() {
        BoundSql eqNull = WhereClauseBuilder.build(WherePredicate.eq("department_id", null), dialect);
        assertThat(eqNull.sql()).isEqualTo("\"department_id\" IS NULL");
        assertThat(eqNull.parameters()).isEmpty();

        BoundSql neqNull = WhereClauseBuilder.build(WherePredicate.neq("department_id", null), dialect);
        assertThat(neqNull.sql()).isEqualTo("\"department_id\" IS NOT NULL");
        assertThat(neqNull.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should render explicit IS NULL and IS NOT NULL predicates")
    void shouldRenderExplicitIsNullPredicates() {
        BoundSql isNull = WhereClauseBuilder.build(WherePredicate.isNull("deleted_at"), dialect);
        assertThat(isNull.sql()).isEqualTo("\"deleted_at\" IS NULL");
        assertThat(isNull.parameters()).isEmpty();

        BoundSql isNotNull = WhereClauseBuilder.build(WherePredicate.isNotNull("created_at"), dialect);
        assertThat(isNotNull.sql()).isEqualTo("\"created_at\" IS NOT NULL");
        assertThat(isNotNull.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should render IN and NOT IN with parameters, and handle empty sets safely")
    void shouldRenderInPredicates() {
        BoundSql in = WhereClauseBuilder.build(WherePredicate.in("status", "ACTIVE", "PENDING", "COMPLETED"), dialect);
        assertThat(in.sql()).isEqualTo("\"status\" IN (?, ?, ?)");
        assertThat(in.parameters()).containsExactly("ACTIVE", "PENDING", "COMPLETED");

        BoundSql notIn = WhereClauseBuilder.build(WherePredicate.notIn("id", 10, 20), dialect);
        assertThat(notIn.sql()).isEqualTo("\"id\" NOT IN (?, ?)");
        assertThat(notIn.parameters()).containsExactly(10, 20);

        // Empty IN should yield '1 = 0' without parameters
        BoundSql emptyIn = WhereClauseBuilder.build(WherePredicate.in("id", List.of()), dialect);
        assertThat(emptyIn.sql()).isEqualTo("1 = 0");
        assertThat(emptyIn.parameters()).isEmpty();

        // Empty NOT IN should yield '1 = 1' without parameters
        BoundSql emptyNotIn = WhereClauseBuilder.build(WherePredicate.notIn("id", List.of()), dialect);
        assertThat(emptyNotIn.sql()).isEqualTo("1 = 1");
        assertThat(emptyNotIn.parameters()).isEmpty();
    }

    @Test
    @DisplayName("Should render BETWEEN and NOT BETWEEN predicates with parameters")
    void shouldRenderBetweenPredicates() {
        BoundSql between = WhereClauseBuilder.build(WherePredicate.between("gpa", 3.0, 4.0), dialect);
        assertThat(between.sql()).isEqualTo("\"gpa\" BETWEEN ? AND ?");
        assertThat(between.parameters()).containsExactly(3.0, 4.0);

        BoundSql notBetween = WhereClauseBuilder.build(WherePredicate.notBetween("age", 18, 65), dialect);
        assertThat(notBetween.sql()).isEqualTo("NOT (\"age\" BETWEEN ? AND ?)");
        assertThat(notBetween.parameters()).containsExactly(18, 65);
    }

    @Test
    @DisplayName("Should render NOT predicate with negation")
    void shouldRenderNotPredicate() {
        BoundSql notEq = WhereClauseBuilder.build(WherePredicate.not(WherePredicate.eq("status", "BANNED")), dialect);
        assertThat(notEq.sql()).isEqualTo("NOT (\"status\" = ?)");
        assertThat(notEq.parameters()).containsExactly("BANNED");
    }

    @Test
    @DisplayName("Should render exact Phase 5 acceptance criteria filter: age >= 18 AND (name LIKE 'A%' OR status = 'ACTIVE')")
    void shouldRenderAcceptanceCriteriaFilter() {
        WherePredicate acceptanceFilter = WherePredicate.and(
                WherePredicate.gte("age", 18),
                WherePredicate.or(
                        WherePredicate.like("name", "A%"),
                        WherePredicate.eq("status", "ACTIVE")
                )
        );

        BoundSql boundSql = WhereClauseBuilder.buildWhereClause(acceptanceFilter, dialect);

        assertThat(boundSql.sql())
                .isEqualTo(" WHERE (\"age\" >= ? AND (\"name\" LIKE ? OR \"status\" = ?))");
        assertThat(boundSql.parameters())
                .containsExactly(18, "A%", "ACTIVE");
    }

    @Test
    @DisplayName("Should render complex 3-level nested predicate tree with proper precedence")
    void shouldRenderComplexNestedTree() {
        WherePredicate complex = WherePredicate.and(
                WherePredicate.in("department_id", 1L, 2L),
                WherePredicate.or(
                        WherePredicate.and(
                                WherePredicate.gte("gpa", 3.8),
                                WherePredicate.eq("active", true)
                        ),
                        WherePredicate.not(
                                WherePredicate.or(
                                        WherePredicate.eq("status", "SUSPENDED"),
                                        WherePredicate.eq("status", "EXPELLED")
                                )
                        )
                )
        );

        BoundSql boundSql = WhereClauseBuilder.buildWhereClause(complex, dialect);

        assertThat(boundSql.sql())
                .isEqualTo(" WHERE (\"department_id\" IN (?, ?) AND ((\"gpa\" >= ? AND \"active\" = ?) OR NOT ((\"status\" = ? OR \"status\" = ?))))");
        assertThat(boundSql.parameters())
                .containsExactly(1L, 2L, 3.8, true, "SUSPENDED", "EXPELLED");
    }

    @Test
    @DisplayName("Should properly quote qualified column identifiers and preserve expressions")
    void shouldProperlyQuoteIdentifiers() {
        BoundSql qualified = WhereClauseBuilder.build(
                WherePredicate.eq("s.student_email", "test@test.com"),
                dialect
        );
        assertThat(qualified.sql()).isEqualTo("\"s\".\"student_email\" = ?");
        assertThat(qualified.parameters()).containsExactly("test@test.com");

        BoundSql fnExpr = WhereClauseBuilder.build(
                WherePredicate.eq("LOWER(email)", "test@test.com"),
                dialect
        );
        assertThat(fnExpr.sql()).isEqualTo("LOWER(email) = ?");
        assertThat(fnExpr.parameters()).containsExactly("test@test.com");
    }

    @Test
    @DisplayName("Should render RawPredicate with positional parameters")
    void shouldRenderRawPredicate() {
        BoundSql raw = WhereClauseBuilder.build(
                WherePredicate.raw("EXTRACT(YEAR FROM created_at) = ? AND is_active = ?", 2024, true),
                dialect
        );
        assertThat(raw.sql()).isEqualTo("EXTRACT(YEAR FROM created_at) = ? AND is_active = ?");
        assertThat(raw.parameters()).containsExactly(2024, true);
    }

    @Test
    @DisplayName("Should reject null dialect or null predicate in build()")
    void shouldRejectNulls() {
        assertThatThrownBy(() -> WhereClauseBuilder.build(null, dialect))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> WhereClauseBuilder.build(WherePredicate.eq("a", 1), null))
                .isInstanceOf(NullPointerException.class);
    }
}
