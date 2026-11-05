package com.graphorm.core.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JoinConditionParser Unit Tests (TASK-8.2)")
class JoinConditionParserTest {

    @Nested
    @DisplayName("Basic Column-to-Column Comparisons")
    class ColumnToColumnTests {

        @Test
        @DisplayName("Should parse double equals 's.departmentId == d.id'")
        void shouldParseDoubleEquals() {
            WherePredicate pred = JoinConditionParser.parse("s.departmentId == d.id");
            assertThat(pred).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);

            WherePredicate.ColumnComparisonPredicate colComp = (WherePredicate.ColumnComparisonPredicate) pred;
            assertThat(colComp.leftColumn()).isEqualTo("s.departmentId");
            assertThat(colComp.operator()).isEqualTo(ComparisonOperator.EQUALS);
            assertThat(colComp.rightColumn()).isEqualTo("d.id");
        }

        @Test
        @DisplayName("Should parse single equals 's.departmentId = d.id'")
        void shouldParseSingleEquals() {
            WherePredicate pred = JoinConditionParser.parse("s.departmentId = d.id");
            assertThat(pred).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);

            WherePredicate.ColumnComparisonPredicate colComp = (WherePredicate.ColumnComparisonPredicate) pred;
            assertThat(colComp.leftColumn()).isEqualTo("s.departmentId");
            assertThat(colComp.operator()).isEqualTo(ComparisonOperator.EQUALS);
            assertThat(colComp.rightColumn()).isEqualTo("d.id");
        }

        @Test
        @DisplayName("Should parse inequality 's.id != e.studentId' and 's.id <> e.studentId'")
        void shouldParseInequality() {
            WherePredicate pred1 = JoinConditionParser.parse("s.id != e.studentId");
            assertThat(pred1).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);
            assertThat(((WherePredicate.ColumnComparisonPredicate) pred1).operator()).isEqualTo(ComparisonOperator.NOT_EQUALS);

            WherePredicate pred2 = JoinConditionParser.parse("s.id <> e.studentId");
            assertThat(pred2).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);
            assertThat(((WherePredicate.ColumnComparisonPredicate) pred2).operator()).isEqualTo(ComparisonOperator.NOT_EQUALS);
        }

        @Test
        @DisplayName("Should parse relational operators '>', '>=', '<', '<='")
        void shouldParseRelationalOperators() {
            assertThat(((WherePredicate.ColumnComparisonPredicate) JoinConditionParser.parse("a.val > b.val")).operator())
                    .isEqualTo(ComparisonOperator.GREATER_THAN);
            assertThat(((WherePredicate.ColumnComparisonPredicate) JoinConditionParser.parse("a.val >= b.val")).operator())
                    .isEqualTo(ComparisonOperator.GREATER_THAN_OR_EQUAL);
            assertThat(((WherePredicate.ColumnComparisonPredicate) JoinConditionParser.parse("a.val < b.val")).operator())
                    .isEqualTo(ComparisonOperator.LESS_THAN);
            assertThat(((WherePredicate.ColumnComparisonPredicate) JoinConditionParser.parse("a.val <= b.val")).operator())
                    .isEqualTo(ComparisonOperator.LESS_THAN_OR_EQUAL);
        }
    }

    @Nested
    @DisplayName("Literal Comparisons & Null Checks")
    class LiteralAndNullTests {

        @Test
        @DisplayName("Should parse string literal comparison")
        void shouldParseStringLiteral() {
            WherePredicate pred = JoinConditionParser.parse("e.status == 'ACTIVE'");
            assertThat(pred).isInstanceOf(WherePredicate.BinaryPredicate.class);

            WherePredicate.BinaryPredicate bin = (WherePredicate.BinaryPredicate) pred;
            assertThat(bin.column()).isEqualTo("e.status");
            assertThat(bin.operator()).isEqualTo(ComparisonOperator.EQUALS);
            assertThat(bin.value()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("Should parse double-quoted string literal")
        void shouldParseDoubleQuotedStringLiteral() {
            WherePredicate pred = JoinConditionParser.parse("e.status == \"ENROLLED\"");
            assertThat(pred).isInstanceOf(WherePredicate.BinaryPredicate.class);
            assertThat(((WherePredicate.BinaryPredicate) pred).value()).isEqualTo("ENROLLED");
        }

        @Test
        @DisplayName("Should parse boolean literals 'true' and 'false'")
        void shouldParseBooleanLiterals() {
            WherePredicate pred1 = JoinConditionParser.parse("e.active == true");
            assertThat(pred1).isInstanceOf(WherePredicate.BinaryPredicate.class);
            assertThat(((WherePredicate.BinaryPredicate) pred1).value()).isEqualTo(Boolean.TRUE);

            WherePredicate pred2 = JoinConditionParser.parse("e.active == false");
            assertThat(pred2).isInstanceOf(WherePredicate.BinaryPredicate.class);
            assertThat(((WherePredicate.BinaryPredicate) pred2).value()).isEqualTo(Boolean.FALSE);
        }

        @Test
        @DisplayName("Should parse integer and float literals")
        void shouldParseNumberLiterals() {
            WherePredicate predInt = JoinConditionParser.parse("e.credits >= 3");
            assertThat(predInt).isInstanceOf(WherePredicate.BinaryPredicate.class);
            assertThat(((WherePredicate.BinaryPredicate) predInt).value()).isEqualTo(3L);

            WherePredicate predFloat = JoinConditionParser.parse("e.gpa >= 3.5");
            assertThat(predFloat).isInstanceOf(WherePredicate.BinaryPredicate.class);
            assertThat(((WherePredicate.BinaryPredicate) predFloat).value()).isEqualTo(3.5);
        }

        @Test
        @DisplayName("Should parse IS NULL and IS NOT NULL")
        void shouldParseIsNull() {
            WherePredicate pred1 = JoinConditionParser.parse("d.deletedAt IS NULL");
            assertThat(pred1).isInstanceOf(WherePredicate.IsNullPredicate.class);
            assertThat(((WherePredicate.IsNullPredicate) pred1).isNull()).isTrue();
            assertThat(((WherePredicate.IsNullPredicate) pred1).column()).isEqualTo("d.deletedAt");

            WherePredicate pred2 = JoinConditionParser.parse("d.deletedAt IS NOT NULL");
            assertThat(pred2).isInstanceOf(WherePredicate.IsNullPredicate.class);
            assertThat(((WherePredicate.IsNullPredicate) pred2).isNull()).isFalse();
        }

        @Test
        @DisplayName("Should parse == null as IS NULL and != null as IS NOT NULL")
        void shouldParseEqualsNull() {
            WherePredicate pred1 = JoinConditionParser.parse("d.deletedAt == null");
            assertThat(pred1).isInstanceOf(WherePredicate.IsNullPredicate.class);
            assertThat(((WherePredicate.IsNullPredicate) pred1).isNull()).isTrue();

            WherePredicate pred2 = JoinConditionParser.parse("d.deletedAt != null");
            assertThat(pred2).isInstanceOf(WherePredicate.IsNullPredicate.class);
            assertThat(((WherePredicate.IsNullPredicate) pred2).isNull()).isFalse();
        }

        @Test
        @DisplayName("Should flip operator when literal is on left-hand side")
        void shouldFlipOperatorForLeftLiteral() {
            WherePredicate pred = JoinConditionParser.parse("'ACTIVE' == e.status");
            assertThat(pred).isInstanceOf(WherePredicate.BinaryPredicate.class);
            WherePredicate.BinaryPredicate bin = (WherePredicate.BinaryPredicate) pred;
            assertThat(bin.column()).isEqualTo("e.status");
            assertThat(bin.value()).isEqualTo("ACTIVE");

            WherePredicate predGt = JoinConditionParser.parse("3.0 < e.gpa");
            assertThat(predGt).isInstanceOf(WherePredicate.BinaryPredicate.class);
            WherePredicate.BinaryPredicate binGt = (WherePredicate.BinaryPredicate) predGt;
            assertThat(binGt.column()).isEqualTo("e.gpa");
            assertThat(binGt.operator()).isEqualTo(ComparisonOperator.GREATER_THAN);
            assertThat(binGt.value()).isEqualTo(3.0);
        }
    }

    @Nested
    @DisplayName("Compound Logical Conditions (AND, OR, Parentheses)")
    class LogicalConditionTests {

        @Test
        @DisplayName("Should parse logical AND with 'AND' and '&&'")
        void shouldParseLogicalAnd() {
            WherePredicate pred1 = JoinConditionParser.parse("s.id == e.studentId AND e.status == 'ACTIVE'");
            assertThat(pred1).isInstanceOf(WherePredicate.AndPredicate.class);
            WherePredicate.AndPredicate and = (WherePredicate.AndPredicate) pred1;
            assertThat(and.predicates()).hasSize(2);
            assertThat(and.predicates().get(0)).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);
            assertThat(and.predicates().get(1)).isInstanceOf(WherePredicate.BinaryPredicate.class);

            WherePredicate pred2 = JoinConditionParser.parse("s.id == e.studentId && e.status == 'ACTIVE'");
            assertThat(pred2).isInstanceOf(WherePredicate.AndPredicate.class);
        }

        @Test
        @DisplayName("Should parse logical OR with 'OR' and '||'")
        void shouldParseLogicalOr() {
            WherePredicate pred1 = JoinConditionParser.parse("s.deptId == d.id OR s.altDeptId == d.id");
            assertThat(pred1).isInstanceOf(WherePredicate.OrPredicate.class);
            WherePredicate.OrPredicate or = (WherePredicate.OrPredicate) pred1;
            assertThat(or.predicates()).hasSize(2);

            WherePredicate pred2 = JoinConditionParser.parse("s.deptId == d.id || s.altDeptId == d.id");
            assertThat(pred2).isInstanceOf(WherePredicate.OrPredicate.class);
        }

        @Test
        @DisplayName("Should respect operator precedence and parentheses")
        void shouldRespectParentheses() {
            WherePredicate pred = JoinConditionParser.parse("(s.deptId == d.id OR s.altDeptId == d.id) AND d.active == true");
            assertThat(pred).isInstanceOf(WherePredicate.AndPredicate.class);
            WherePredicate.AndPredicate and = (WherePredicate.AndPredicate) pred;
            assertThat(and.predicates()).hasSize(2);
            assertThat(and.predicates().get(0)).isInstanceOf(WherePredicate.OrPredicate.class);
            assertThat(and.predicates().get(1)).isInstanceOf(WherePredicate.BinaryPredicate.class);
        }
    }

    @Nested
    @DisplayName("Column Resolver Support")
    class ColumnResolverTests {

        @Test
        @DisplayName("Should resolve column names when columnResolver is provided")
        void shouldResolveColumns() {
            Map<String, String> mapping = Map.of(
                    "s.departmentId", "s.dept_id",
                    "d.id", "d.department_id"
            );

            WherePredicate pred = JoinConditionParser.parse("s.departmentId == d.id", mapping::get);
            assertThat(pred).isInstanceOf(WherePredicate.ColumnComparisonPredicate.class);

            WherePredicate.ColumnComparisonPredicate colComp = (WherePredicate.ColumnComparisonPredicate) pred;
            assertThat(colComp.leftColumn()).isEqualTo("s.dept_id");
            assertThat(colComp.rightColumn()).isEqualTo("d.department_id");
        }
    }

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should reject null or blank condition")
        void shouldRejectNullOrBlank() {
            assertThatThrownBy(() -> JoinConditionParser.parse(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> JoinConditionParser.parse(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JoinConditionParser.parse("   "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("Should reject malformed syntax")
        void shouldRejectMalformedSyntax() {
            assertThatThrownBy(() -> JoinConditionParser.parse("s.id =="))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JoinConditionParser.parse("== d.id"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JoinConditionParser.parse("(s.id == d.id"))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> JoinConditionParser.parse("'ACTIVE' == 'INACTIVE'"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
