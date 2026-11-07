package com.graphorm.graphql.planner;

import com.graphorm.core.metadata.FieldMetadata;
import com.graphorm.core.metadata.ModelMetadata;
import com.graphorm.core.metadata.ModelRegistry;
import com.graphorm.core.metadata.RelationMetadata;
import com.graphorm.core.metadata.RelationType;
import com.graphorm.core.query.ProjectedColumn;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.SelectedField;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SelectionSetAnalyzer Unit Tests (TASK-11.1, TASK-11.2, TASK-11.3)")
class SelectionSetAnalyzerTest {

    private ModelMetadata studentModel;
    private ModelMetadata departmentModel;
    private ModelRegistry modelRegistry;

    @BeforeEach
    void setUp() {
        studentModel = ModelMetadata.builder("Student")
                .tableName("students")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("email", String.class).build())
                .addField(FieldMetadata.builder("departmentId", Long.class).dbColumnName("department_id").build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("department")
                        .relationType(RelationType.MANY_TO_ONE)
                        .sourceModel("Student")
                        .targetModel("Department")
                        .foreignKeyField("departmentId")
                        .referenceField("id")
                        .build())
                .build();

        departmentModel = ModelMetadata.builder("Department")
                .tableName("departments")
                .addField(FieldMetadata.builder("id", Long.class).primaryKey(true).build())
                .addField(FieldMetadata.builder("name", String.class).build())
                .addField(FieldMetadata.builder("code", String.class).build())
                .addPrimaryKeyField("id")
                .addRelation(RelationMetadata.builder("students")
                        .relationType(RelationType.ONE_TO_MANY)
                        .sourceModel("Department")
                        .targetModel("Student")
                        .build())
                .build();

        modelRegistry = ModelRegistry.builder()
                .addModel(studentModel)
                .addModel(departmentModel)
                .build();
    }

    @Test
    @DisplayName("TASK-11.1: Extracts requested field tree and filters meta-fields")
    void shouldExtractFieldTreeFilteringMetaFields() {
        SelectedField fName = new StubSelectedField("name", null, null);
        SelectedField fTypeName = new StubSelectedField("__typename", null, null);
        SelectedField fDeptName = new StubSelectedField("name", null, null);
        StubSelectionSet deptSet = new StubSelectionSet(List.of(fDeptName));
        SelectedField fDept = new StubSelectedField("department", null, deptSet);

        StubSelectionSet rootSet = new StubSelectionSet(List.of(fName, fTypeName, fDept));

        Map<String, FieldTreeNode> tree = SelectionSetAnalyzer.extractTree(rootSet);

        assertThat(tree).containsOnlyKeys("name", "department");
        assertThat(tree.get("name").isLeaf()).isTrue();
        assertThat(tree.get("department").hasChildren()).isTrue();
        assertThat(tree.get("department").child("name")).isNotNull();
    }

    @Test
    @DisplayName("TASK-11.2: Selective column projection - projects only requested scalar columns")
    void shouldProjectOnlyRequestedScalarColumns() {
        SelectedField fName = new StubSelectedField("name", null, null);
        StubSelectionSet rootSet = new StubSelectionSet(List.of(fName));

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(rootSet, studentModel, modelRegistry);

        assertThat(analyzed.requestedScalarFields()).containsExactly("name");
        assertThat(analyzed.hiddenDependencyFields()).isEmpty();
        assertThat(analyzed.projectedColumns()).hasSize(1);
        assertThat(analyzed.projectedColumns().get(0).expression()).isEqualTo("name");
        assertThat(analyzed.projectedColumns().get(0).isHiddenDependency()).isFalse();
    }

    @Test
    @DisplayName("TASK-11.3: Auto-injects hidden dependency foreign key when relation is requested without FK")
    void shouldAutoInjectHiddenForeignKeyWhenRelationRequested() {
        // Query: students { name department { name } }
        // Notice: departmentId is NOT in the selection set!
        SelectedField fName = new StubSelectedField("name", null, null);
        SelectedField fDeptName = new StubSelectedField("name", null, null);
        StubSelectionSet deptSet = new StubSelectionSet(List.of(fDeptName));
        SelectedField fDept = new StubSelectedField("department", null, deptSet);

        StubSelectionSet rootSet = new StubSelectionSet(List.of(fName, fDept));

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(rootSet, studentModel, modelRegistry);

        assertThat(analyzed.requestedScalarFields()).containsExactly("name");
        assertThat(analyzed.isRelationRequested("department")).isTrue();

        // Must inject department_id as hidden dependency
        assertThat(analyzed.hiddenDependencyFields()).contains("departmentId", "department_id");
        assertThat(analyzed.hasHiddenDependencies()).isTrue();

        List<ProjectedColumn> columns = analyzed.projectedColumns();
        assertThat(columns).hasSize(2);

        ProjectedColumn nameCol = columns.stream().filter(c -> c.expression().equals("name")).findFirst().orElseThrow();
        assertThat(nameCol.isHiddenDependency()).isFalse();

        ProjectedColumn fkCol = columns.stream().filter(c -> c.expression().equals("department_id")).findFirst().orElseThrow();
        assertThat(fkCol.isHiddenDependency()).isTrue();
    }

    @Test
    @DisplayName("TASK-11.3: Does not duplicate foreign key if client already requested it explicitly")
    void shouldNotDuplicateForeignKeyIfAlreadyRequested() {
        SelectedField fName = new StubSelectedField("name", null, null);
        SelectedField fDeptId = new StubSelectedField("departmentId", null, null);
        SelectedField fDeptName = new StubSelectedField("name", null, null);
        StubSelectionSet deptSet = new StubSelectionSet(List.of(fDeptName));
        SelectedField fDept = new StubSelectedField("department", null, deptSet);

        StubSelectionSet rootSet = new StubSelectionSet(List.of(fName, fDeptId, fDept));

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(rootSet, studentModel, modelRegistry);

        assertThat(analyzed.requestedScalarFields()).containsExactlyInAnyOrder("name", "departmentId");
        assertThat(analyzed.hiddenDependencyFields()).isEmpty();
        assertThat(analyzed.projectedColumns()).hasSize(2);
        assertThat(analyzed.projectedColumns().stream().allMatch(c -> !c.isHiddenDependency())).isTrue();
    }

    @Test
    @DisplayName("TASK-11.3: Auto-injects primary key when 1:N relation is requested on parent")
    void shouldAutoInjectPrimaryKeyOnParentForOneToManyRelation() {
        // Query: departments { name students { name } }
        // Notice: Department.id is NOT in the selection set!
        SelectedField fName = new StubSelectedField("name", null, null);
        SelectedField fStudentName = new StubSelectedField("name", null, null);
        StubSelectionSet studentSet = new StubSelectionSet(List.of(fStudentName));
        SelectedField fStudents = new StubSelectedField("students", null, studentSet);

        StubSelectionSet rootSet = new StubSelectionSet(List.of(fName, fStudents));

        AnalyzedSelectionSet analyzed = SelectionSetAnalyzer.analyze(rootSet, departmentModel, modelRegistry);

        assertThat(analyzed.requestedScalarFields()).containsExactly("name");
        assertThat(analyzed.isRelationRequested("students")).isTrue();

        // Department.id must be auto-injected as hidden dependency
        assertThat(analyzed.hiddenDependencyFields()).contains("id");
        assertThat(analyzed.projectedColumns()).hasSize(2);

        ProjectedColumn idCol = analyzed.projectedColumns().stream()
                .filter(c -> c.expression().equals("id"))
                .findFirst()
                .orElseThrow();
        assertThat(idCol.isHiddenDependency()).isTrue();
    }

    @Test
    @DisplayName("Falls back to all fields when selection set is empty or null")
    void shouldFallbackToAllFieldsWhenEmpty() {
        AnalyzedSelectionSet analyzedNull = SelectionSetAnalyzer.analyze((DataFetchingFieldSelectionSet) null, studentModel, modelRegistry);
        assertThat(analyzedNull.requestedScalarFields()).containsExactlyInAnyOrder("id", "name", "email", "departmentId");
        assertThat(analyzedNull.projectedColumns()).hasSize(4);

        AnalyzedSelectionSet analyzedEmpty = SelectionSetAnalyzer.analyze(new StubSelectionSet(List.of()), studentModel, modelRegistry);
        assertThat(analyzedEmpty.requestedScalarFields()).containsExactlyInAnyOrder("id", "name", "email", "departmentId");
        assertThat(analyzedEmpty.projectedColumns()).hasSize(4);
    }

    // --- Helper Stubs for DataFetchingFieldSelectionSet ---

    private record StubSelectedField(
            String name,
            String alias,
            DataFetchingFieldSelectionSet selectionSet
    ) implements SelectedField {

        @Override public String getName() { return name; }
        @Override public String getQualifiedName() { return name; }
        @Override public String getFullyQualifiedName() { return name; }
        @Override public List<GraphQLObjectType> getObjectTypes() { return List.of(); }
        @Override public List<String> getObjectTypeNames() { return List.of(); }
        @Override public List<GraphQLFieldDefinition> getFieldDefinitions() { return List.of(); }
        @Override public GraphQLOutputType getType() { return null; }
        @Override public Map<String, Object> getArguments() { return Map.of(); }
        @Override public int getLevel() { return 1; }
        @Override public boolean isConditional() { return false; }
        @Override public String getAlias() { return alias; }
        @Override public String getResultKey() { return alias != null ? alias : name; }
        @Override public SelectedField getParentField() { return null; }
        @Override public DataFetchingFieldSelectionSet getSelectionSet() { return selectionSet; }
    }

    private record StubSelectionSet(List<SelectedField> immediateFields) implements DataFetchingFieldSelectionSet {
        @Override public boolean contains(String s) { return false; }
        @Override public boolean containsAnyOf(String s, String... strings) { return false; }
        @Override public boolean containsAllOf(String s, String... strings) { return false; }
        @Override public List<SelectedField> getFields() { return immediateFields; }
        @Override public List<SelectedField> getImmediateFields() { return immediateFields; }
        @Override public List<SelectedField> getFields(String s, String... strings) { return List.of(); }
        @Override public Map<String, List<SelectedField>> getFieldsGroupedByResultKey() { return Map.of(); }
        @Override public Map<String, List<SelectedField>> getFieldsGroupedByResultKey(String s, String... strings) { return Map.of(); }
    }
}
