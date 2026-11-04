package com.graphorm.core.query;

import com.graphorm.core.exception.ReadOnlyViewException;
import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ViewModelMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Query AST & Read-Only Guard Unit Tests")
class QueryAstTest {

    @Test
    @DisplayName("SourceTable: PhysicalTable, DatabaseView, and SubquerySource")
    void testSourceTableVariants() {
        SourceTable.PhysicalTable table = SourceTable.table("users");
        assertThat(table.identifier()).isEqualTo("users");
        assertThat(table.isView()).isFalse();
        assertThat(table.isSubquery()).isFalse();

        SourceTable.DatabaseView view = SourceTable.view("vw_active_users");
        assertThat(view.identifier()).isEqualTo("vw_active_users");
        assertThat(view.isView()).isTrue();
        assertThat(view.isSubquery()).isFalse();

        SelectQuery innerQuery = SelectQuery.from("orders").column("id").build();
        SourceTable.SubquerySource subquery = SourceTable.subquery(innerQuery, "sub");
        assertThat(subquery.identifier()).isEqualTo("sub");
        assertThat(subquery.isView()).isFalse();
        assertThat(subquery.isSubquery()).isTrue();

        assertThatThrownBy(() -> SourceTable.table(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SourceTable.view(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> SourceTable.subquery(null, "s"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ProjectedColumn: Unaliased, aliased, and hidden dependency columns")
    void testProjectedColumn() {
        ProjectedColumn col1 = ProjectedColumn.of("s.name");
        assertThat(col1.expression()).isEqualTo("s.name");
        assertThat(col1.alias()).isNull();
        assertThat(col1.isHiddenDependency()).isFalse();

        ProjectedColumn col2 = ProjectedColumn.of("s.id", "studentId");
        assertThat(col2.expression()).isEqualTo("s.id");
        assertThat(col2.alias()).isEqualTo("studentId");
        assertThat(col2.isHiddenDependency()).isFalse();

        ProjectedColumn col3 = ProjectedColumn.hidden("s.departmentId", "__hidden_dept_id");
        assertThat(col3.isHiddenDependency()).isTrue();

        assertThatThrownBy(() -> ProjectedColumn.of(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> ProjectedColumn.of(" "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("JoinClause: Inner, left, right, full joins and validations")
    void testJoinClause() {
        WherePredicate onCond = WherePredicate.eq("s.deptId", "d.id");
        JoinClause leftJoin = JoinClause.left("departments", "d", onCond);

        assertThat(leftJoin.joinType()).isEqualTo(JoinType.LEFT);
        assertThat(leftJoin.alias()).isEqualTo("d");
        assertThat(leftJoin.condition()).isEqualTo(onCond);
        assertThat(leftJoin.table().identifier()).isEqualTo("departments");

        JoinClause innerJoin = JoinClause.inner(SourceTable.table("courses"), "c", onCond);
        assertThat(innerJoin.joinType()).isEqualTo(JoinType.INNER);

        JoinClause rightJoin = JoinClause.right("instructors", "i", onCond);
        assertThat(rightJoin.joinType()).isEqualTo(JoinType.RIGHT);

        JoinClause fullJoin = JoinClause.full(SourceTable.table("enrollments"), "e", onCond);
        assertThat(fullJoin.joinType()).isEqualTo(JoinType.FULL);

        assertThatThrownBy(() -> new JoinClause(null, SourceTable.table("t"), "a", onCond))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new JoinClause(JoinType.LEFT, SourceTable.table("t"), " ", onCond))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("WherePredicate: Composable tree with binary, in, isNull, between, and, or, not, and raw")
    void testWherePredicateComposableTree() {
        // Binary predicates
        WherePredicate eq = WherePredicate.eq("age", 21);
        WherePredicate neq = WherePredicate.neq("status", "DELETED");
        WherePredicate gt = WherePredicate.gt("score", 90);
        WherePredicate gte = WherePredicate.gte("credits", 120);
        WherePredicate lt = WherePredicate.lt("balance", 0);
        WherePredicate lte = WherePredicate.lte("rank", 10);
        WherePredicate like = WherePredicate.like("name", "John%");
        WherePredicate ilike = WherePredicate.ilike("email", "%@domain.com");

        assertThat(eq).isInstanceOf(WherePredicate.BinaryPredicate.class);
        assertThat(((WherePredicate.BinaryPredicate) eq).operator()).isEqualTo(ComparisonOperator.EQUALS);
        assertThat(((WherePredicate.BinaryPredicate) neq).operator()).isEqualTo(ComparisonOperator.NOT_EQUALS);
        assertThat(((WherePredicate.BinaryPredicate) gt).operator()).isEqualTo(ComparisonOperator.GREATER_THAN);
        assertThat(((WherePredicate.BinaryPredicate) gte).operator()).isEqualTo(ComparisonOperator.GREATER_THAN_OR_EQUAL);
        assertThat(((WherePredicate.BinaryPredicate) lt).operator()).isEqualTo(ComparisonOperator.LESS_THAN);
        assertThat(((WherePredicate.BinaryPredicate) lte).operator()).isEqualTo(ComparisonOperator.LESS_THAN_OR_EQUAL);
        assertThat(((WherePredicate.BinaryPredicate) like).operator()).isEqualTo(ComparisonOperator.LIKE);
        assertThat(((WherePredicate.BinaryPredicate) ilike).operator()).isEqualTo(ComparisonOperator.ILIKE);

        // IN & NOT IN
        WherePredicate inPred = WherePredicate.in("status", "ACTIVE", "PENDING");
        assertThat(inPred).isInstanceOf(WherePredicate.InPredicate.class);
        assertThat(((WherePredicate.InPredicate) inPred).isNotIn()).isFalse();
        assertThat(((WherePredicate.InPredicate) inPred).values()).containsExactly("ACTIVE", "PENDING");

        WherePredicate notInPred = WherePredicate.notIn("role", List.of("BANNED", "SUSPENDED"));
        assertThat(((WherePredicate.InPredicate) notInPred).isNotIn()).isTrue();

        // IS NULL & IS NOT NULL
        WherePredicate isNull = WherePredicate.isNull("deletedAt");
        assertThat(((WherePredicate.IsNullPredicate) isNull).isNull()).isTrue();
        WherePredicate isNotNull = WherePredicate.isNotNull("email");
        assertThat(((WherePredicate.IsNullPredicate) isNotNull).isNull()).isFalse();

        // BETWEEN
        WherePredicate between = WherePredicate.between("age", 18, 30);
        assertThat(between).isInstanceOf(WherePredicate.BetweenPredicate.class);
        WherePredicate.BetweenPredicate bp = (WherePredicate.BetweenPredicate) between;
        assertThat(bp.lower()).isEqualTo(18);
        assertThat(bp.upper()).isEqualTo(30);

        // AND, OR, NOT combinators with flattening
        WherePredicate combined = eq.and(gt).or(like.negate());
        assertThat(combined).isInstanceOf(WherePredicate.OrPredicate.class);

        WherePredicate multiAnd = WherePredicate.and(eq, gt, lt);
        assertThat(multiAnd).isInstanceOf(WherePredicate.AndPredicate.class);
        assertThat(((WherePredicate.AndPredicate) multiAnd).predicates()).hasSize(3);

        // Flattening of nested AND
        WherePredicate nestedAnd = WherePredicate.and(multiAnd, neq);
        assertThat(((WherePredicate.AndPredicate) nestedAnd).predicates()).hasSize(4);

        // NOT negation unwraps NotPredicate
        WherePredicate negated = WherePredicate.not(eq);
        assertThat(negated).isInstanceOf(WherePredicate.NotPredicate.class);
        assertThat(WherePredicate.not(negated)).isEqualTo(eq);

        // Raw SQL predicate
        WherePredicate raw = WherePredicate.raw("MOD(id, 2) = ?", 0);
        assertThat(raw).isInstanceOf(WherePredicate.RawPredicate.class);
        assertThat(((WherePredicate.RawPredicate) raw).parameters()).containsExactly(0);

        // Immutability of InPredicate values
        List<Object> values = new ArrayList<>(List.of("A", "B"));
        WherePredicate inWithList = WherePredicate.in("col", values);
        values.add("C");
        assertThat(((WherePredicate.InPredicate) inWithList).values()).containsExactly("A", "B");
    }

    @Test
    @DisplayName("HavingPredicate, GroupByClause, and OrderByClause")
    void testHavingGroupByOrderBy() {
        HavingPredicate having = HavingPredicate.of(WherePredicate.gt("COUNT(e.id)", 5));
        assertThat(having.predicate()).isNotNull();

        GroupByClause groupBy = GroupByClause.of("s.id");
        assertThat(groupBy.expression()).isEqualTo("s.id");
        assertThatThrownBy(() -> GroupByClause.of(" "))
                .isInstanceOf(IllegalArgumentException.class);

        OrderByClause asc = OrderByClause.asc("s.name");
        assertThat(asc.direction()).isEqualTo(OrderDirection.ASC);
        assertThat(asc.nullsOrder()).isEqualTo(NullsOrder.DEFAULT);

        OrderByClause desc = OrderByClause.desc("gpa", NullsOrder.LAST);
        assertThat(desc.direction()).isEqualTo(OrderDirection.DESC);
        assertThat(desc.nullsOrder()).isEqualTo(NullsOrder.LAST);
    }

    @Test
    @DisplayName("SelectQuery: Full query construction and immutability")
    void testSelectQuery() {
        SelectQuery query = SelectQuery.from("students")
                .as("s")
                .column("s.id", "id")
                .column("s.name", "name")
                .hiddenColumn("s.dept_id", "__dept_id")
                .join(JoinClause.left("departments", "d", WherePredicate.eq("s.dept_id", "d.id")))
                .where(WherePredicate.eq("s.active", true))
                .groupBy("s.id")
                .having(WherePredicate.gt("COUNT(*)", 1))
                .orderByAsc("s.name")
                .limit(20)
                .offset(40)
                .build();

        assertThat(query.queryType()).isEqualTo(QueryType.SELECT);
        assertThat(query.source().identifier()).isEqualTo("students");
        assertThat(query.tableAlias()).isEqualTo("s");
        assertThat(query.columns()).hasSize(3);
        assertThat(query.joins()).hasSize(1);
        assertThat(query.where()).isNotNull();
        assertThat(query.groupBy()).hasSize(1);
        assertThat(query.having()).isNotNull();
        assertThat(query.orderBy()).hasSize(1);
        assertThat(query.limit()).isEqualTo(20);
        assertThat(query.offset()).isEqualTo(40);

        // Assert immutability
        assertThatThrownBy(() -> query.columns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> query.joins().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> query.groupBy().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> query.orderBy().clear())
                .isInstanceOf(UnsupportedOperationException.class);

        // Rejection of negative limit/offset
        assertThatThrownBy(() -> SelectQuery.from("s").limit(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Limit cannot be negative");

        assertThatThrownBy(() -> SelectQuery.from("s").offset(-5).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Offset cannot be negative");
    }

    @Test
    @DisplayName("Write Queries: InsertQuery, UpdateQuery, and DeleteQuery immutability")
    void testWriteQueries() {
        Map<String, Object> values = new HashMap<>();
        values.put("name", "Alice");
        values.put("email", "alice@example.com");

        InsertQuery insert = InsertQuery.into("students")
                .values(values)
                .build();

        assertThat(insert.queryType()).isEqualTo(QueryType.INSERT);
        assertThat(insert.table().identifier()).isEqualTo("students");
        assertThat(insert.values()).containsEntry("name", "Alice");

        // Mutating original values map does not mutate query
        values.put("hacked", "true");
        assertThat(insert.values()).doesNotContainKey("hacked");

        // Mutating returned values map throws UnsupportedOperationException
        assertThatThrownBy(() -> insert.values().put("bad", "bad"))
                .isInstanceOf(UnsupportedOperationException.class);

        // UpdateQuery
        UpdateQuery update = UpdateQuery.table("students")
                .set("name", "Bob")
                .where(WherePredicate.eq("id", 1))
                .build();
        assertThat(update.queryType()).isEqualTo(QueryType.UPDATE);
        assertThat(update.values()).containsEntry("name", "Bob");
        assertThat(update.where()).isNotNull();

        // DeleteQuery
        DeleteQuery delete = DeleteQuery.from("students")
                .where(WherePredicate.eq("id", 1))
                .build();
        assertThat(delete.queryType()).isEqualTo(QueryType.DELETE);
        assertThat(delete.table().identifier()).isEqualTo("students");
        assertThat(delete.where()).isNotNull();

        // Reject empty values
        assertThatThrownBy(() -> InsertQuery.into("students").values(Map.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insert query must specify at least one column value");

        assertThatThrownBy(() -> UpdateQuery.table("students").values(Map.of()).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Update query must specify at least one column value");
    }

    @Test
    @DisplayName("QueryGuards: Writable model succeeds, read-only view model throws ReadOnlyViewException")
    void testQueryGuardsAndReadOnlyEnforcement() {
        ModelMetadata studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .build();

        ViewModelMetadata studentView = ViewModelMetadata.builder("StudentSummary")
                .viewName("vw_student_summary")
                .databaseBacked(true)
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .build();

        // Model is writable: should succeed without exception
        QueryGuards.assertWritable(studentModel, "INSERT");
        QueryGuards.assertWritable(studentModel, "UPDATE");
        QueryGuards.assertWritable(studentModel, "DELETE");

        InsertQuery insert = QueryGuards.createInsert(studentModel, Map.of("id", 1L));
        assertThat(insert.table().tableName()).isEqualTo("students");

        UpdateQuery update = QueryGuards.createUpdate(studentModel, Map.of("id", 1L), WherePredicate.eq("id", 1L));
        assertThat(update.table().tableName()).isEqualTo("students");

        DeleteQuery delete = QueryGuards.createDelete(studentModel, WherePredicate.eq("id", 1L));
        assertThat(delete.table().tableName()).isEqualTo("students");

        // View model is read-only: MUST throw ReadOnlyViewException
        assertThatThrownBy(() -> QueryGuards.assertWritable(studentView, "INSERT"))
                .isInstanceOf(ReadOnlyViewException.class)
                .hasMessageContaining("Cannot perform INSERT on read-only view 'StudentSummary'")
                .matches(e -> "ERR_READ_ONLY_VIEW".equals(((ReadOnlyViewException) e).getErrorCode()))
                .matches(e -> "StudentSummary".equals(((ReadOnlyViewException) e).getEntityName()))
                .matches(e -> "INSERT".equals(((ReadOnlyViewException) e).getOperation()));

        assertThatThrownBy(() -> QueryGuards.createInsert(studentView, Map.of("id", 1L)))
                .isInstanceOf(ReadOnlyViewException.class);

        assertThatThrownBy(() -> QueryGuards.createUpdate(studentView, Map.of("id", 1L), WherePredicate.eq("id", 1L)))
                .isInstanceOf(ReadOnlyViewException.class);

        assertThatThrownBy(() -> QueryGuards.createDelete(studentView, WherePredicate.eq("id", 1L)))
                .isInstanceOf(ReadOnlyViewException.class);

        // DatabaseView source table is read-only
        SourceTable.DatabaseView dbViewSource = SourceTable.view("vw_test");
        assertThatThrownBy(() -> QueryGuards.assertNotView(dbViewSource, "DELETE"))
                .isInstanceOf(ReadOnlyViewException.class);
    }
}
