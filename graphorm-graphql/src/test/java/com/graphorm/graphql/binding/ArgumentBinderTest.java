package com.graphorm.graphql.binding;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.query.ComparisonOperator;
import com.graphorm.core.query.OrderByClause;
import com.graphorm.core.query.OrderDirection;
import com.graphorm.core.query.WherePredicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("GraphQL ArgumentBinder Unit Tests (TASK-10.5)")
class ArgumentBinderTest {

    private ArgumentBinder binder;
    private ModelMetadata studentModel;

    @BeforeEach
    void setUp() {
        binder = new ArgumentBinder();
        studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).dbColumnName("id").build())
                .addField(FieldMetadata.builder("name", String.class).dbColumnName("student_name").build())
                .addField(FieldMetadata.builder("email", String.class).dbColumnName("email_address").build())
                .addField(FieldMetadata.builder("gpa", Double.class).dbColumnName("gpa").build())
                .addField(FieldMetadata.builder("age", Integer.class).dbColumnName("age").build())
                .addField(FieldMetadata.builder("status", String.class).dbColumnName("status").build())
                .addPrimaryKeyField("id")
                .build();
    }

    @Test
    @DisplayName("bindWhere: returns null when argument is null or empty map")
    void testBindWhereNullOrEmpty() {
        assertThat(binder.bindWhere((Object) null, studentModel)).isNull();
        assertThat(binder.bindWhere(Map.of(), studentModel)).isNull();
    }

    @Test
    @DisplayName("bindWhere: throws IllegalArgumentException if argument is not a Map")
    void testBindWhereInvalidType() {
        assertThatThrownBy(() -> binder.bindWhere("invalid", studentModel))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected 'where' argument to be a Map");
    }

    @Test
    @DisplayName("bindWhere: direct scalar equality resolves dbColumnName")
    void testBindWhereDirectScalar() {
        WherePredicate p = binder.bindWhere(Map.of("name", "Alice"), studentModel);
        assertThat(p).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate b = (WherePredicate.BinaryPredicate) p;
        assertThat(b.column()).isEqualTo("student_name");
        assertThat(b.operator()).isEqualTo(ComparisonOperator.EQUALS);
        assertThat(b.value()).isEqualTo("Alice");
    }

    @Test
    @DisplayName("bindWhere: null value maps to IsNullPredicate")
    void testBindWhereNullValue() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("status", null);
        WherePredicate p = binder.bindWhere(map, studentModel);
        assertThat(p).isInstanceOf(WherePredicate.IsNullPredicate.class);
        WherePredicate.IsNullPredicate isNull = (WherePredicate.IsNullPredicate) p;
        assertThat(isNull.column()).isEqualTo("status");
        assertThat(isNull.isNull()).isTrue();
    }

    @Test
    @DisplayName("bindWhere: comparison operators (eq, neq, gt, gte, lt, lte)")
    void testBindWhereComparisonOperators() {
        WherePredicate pGt = binder.bindWhere(Map.of("gpa", Map.of("gt", 3.5)), studentModel);
        assertThat(pGt).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate bGt = (WherePredicate.BinaryPredicate) pGt;
        assertThat(bGt.column()).isEqualTo("gpa");
        assertThat(bGt.operator()).isEqualTo(ComparisonOperator.GREATER_THAN);
        assertThat(bGt.value()).isEqualTo(3.5);

        WherePredicate pLte = binder.bindWhere(Map.of("age", Map.of("lte", 25)), studentModel);
        assertThat(pLte).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate bLte = (WherePredicate.BinaryPredicate) pLte;
        assertThat(bLte.column()).isEqualTo("age");
        assertThat(bLte.operator()).isEqualTo(ComparisonOperator.LESS_THAN_OR_EQUAL);
        assertThat(bLte.value()).isEqualTo(25);
    }

    @Test
    @DisplayName("bindWhere: string matching operators (contains, startsWith, endsWith, ilike)")
    void testBindWhereStringOperators() {
        WherePredicate pContains = binder.bindWhere(Map.of("name", Map.of("contains", "Bob")), studentModel);
        assertThat(pContains).isInstanceOf(WherePredicate.BinaryPredicate.class);
        WherePredicate.BinaryPredicate b = (WherePredicate.BinaryPredicate) pContains;
        assertThat(b.column()).isEqualTo("student_name");
        assertThat(b.operator()).isEqualTo(ComparisonOperator.LIKE);
        assertThat(b.value()).isEqualTo("%Bob%");

        WherePredicate pStartsWith = binder.bindWhere(Map.of("email", Map.of("startsWith", "admin")), studentModel);
        WherePredicate.BinaryPredicate bStart = (WherePredicate.BinaryPredicate) pStartsWith;
        assertThat(bStart.column()).isEqualTo("email_address");
        assertThat(bStart.value()).isEqualTo("admin%");
    }

    @Test
    @DisplayName("bindWhere: in and notIn operators")
    void testBindWhereInAndNotIn() {
        WherePredicate pIn = binder.bindWhere(Map.of("age", Map.of("in", List.of(20, 21, 22))), studentModel);
        assertThat(pIn).isInstanceOf(WherePredicate.InPredicate.class);
        WherePredicate.InPredicate in = (WherePredicate.InPredicate) pIn;
        assertThat(in.column()).isEqualTo("age");
        assertThat(in.values()).containsExactly(20, 21, 22);
        assertThat(in.isNotIn()).isFalse();

        WherePredicate pNotIn = binder.bindWhere(Map.of("status", Map.of("notIn", List.of("SUSPENDED"))), studentModel);
        assertThat(pNotIn).isInstanceOf(WherePredicate.InPredicate.class);
        WherePredicate.InPredicate notIn = (WherePredicate.InPredicate) pNotIn;
        assertThat(notIn.isNotIn()).isTrue();
    }

    @Test
    @DisplayName("bindWhere: between and notBetween operators")
    void testBindWhereBetween() {
        WherePredicate pBetween = binder.bindWhere(Map.of("gpa", Map.of("between", List.of(3.0, 4.0))), studentModel);
        assertThat(pBetween).isInstanceOf(WherePredicate.BetweenPredicate.class);
        WherePredicate.BetweenPredicate between = (WherePredicate.BetweenPredicate) pBetween;
        assertThat(between.column()).isEqualTo("gpa");
        assertThat(between.lower()).isEqualTo(3.0);
        assertThat(between.upper()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("bindWhere: logical operators AND, OR, NOT composable tree")
    void testBindWhereLogicalOperators() {
        Map<String, Object> andMap = Map.of(
                "AND", List.of(
                        Map.of("age", Map.of("gte", 18)),
                        Map.of("status", "ACTIVE")
                )
        );
        WherePredicate pAnd = binder.bindWhere(andMap, studentModel);
        assertThat(pAnd).isInstanceOf(WherePredicate.AndPredicate.class);
        WherePredicate.AndPredicate and = (WherePredicate.AndPredicate) pAnd;
        assertThat(and.predicates()).hasSize(2);

        Map<String, Object> orMap = Map.of(
                "OR", List.of(
                        Map.of("status", "ACTIVE"),
                        Map.of("status", "PROBATION")
                )
        );
        WherePredicate pOr = binder.bindWhere(orMap, studentModel);
        assertThat(pOr).isInstanceOf(WherePredicate.OrPredicate.class);

        Map<String, Object> notMap = Map.of(
                "NOT", Map.of("status", "EXPELLED")
        );
        WherePredicate pNot = binder.bindWhere(notMap, studentModel);
        assertThat(pNot).isInstanceOf(WherePredicate.NotPredicate.class);
    }

    @Test
    @DisplayName("bindOrderBy: maps, lists, and string direction parsing")
    void testBindOrderBy() {
        List<OrderByClause> fromMap = binder.bindOrderBy(Map.of("name", "ASC", "gpa", "DESC"), studentModel);
        assertThat(fromMap).hasSize(2);

        List<OrderByClause> fromList = binder.bindOrderBy(List.of(
                Map.of("name", "ASC"),
                Map.of("gpa", "DESC")
        ), studentModel);
        assertThat(fromList).hasSize(2);
        assertThat(fromList.get(0).expression()).isEqualTo("student_name");
        assertThat(fromList.get(0).direction()).isEqualTo(OrderDirection.ASC);
        assertThat(fromList.get(1).expression()).isEqualTo("gpa");
        assertThat(fromList.get(1).direction()).isEqualTo(OrderDirection.DESC);

        List<OrderByClause> fromString = binder.bindOrderBy("name_DESC", studentModel);
        assertThat(fromString).hasSize(1);
        assertThat(fromString.get(0).expression()).isEqualTo("student_name");
        assertThat(fromString.get(0).direction()).isEqualTo(OrderDirection.DESC);
    }
}
