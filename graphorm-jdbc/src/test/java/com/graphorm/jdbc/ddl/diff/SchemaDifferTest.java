package com.graphorm.jdbc.ddl.diff;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.core.metadata.IndexType;
import com.graphorm.core.metadata.ReferentialAction;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.DatabaseSchema;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDifferTest {

    @Test
    @DisplayName("Should detect newly created tables and dropped tables")
    void shouldDetectCreatedAndDroppedTables() {
        TableDefinition usersTable = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .primaryKeyColumns(List.of("id"))
                .build();

        TableDefinition ordersTable = TableDefinition.builder("orders")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .primaryKeyColumns(List.of("id"))
                .build();

        DatabaseSchema baseline = DatabaseSchema.builder()
                .addTable(usersTable)
                .build();

        DatabaseSchema target = DatabaseSchema.builder()
                .addTable(ordersTable)
                .build();

        SchemaDiff diff = SchemaDiffer.diff(baseline, target);

        assertThat(diff.isEmpty()).isFalse();
        assertThat(diff.createdTables()).extracting(TableDefinition::name).containsExactly("orders");
        assertThat(diff.droppedTables()).extracting(TableDefinition::name).containsExactly("users");
        assertThat(diff.alteredTables()).isEmpty();
    }

    @Test
    @DisplayName("Should detect column additions, removals, and modifications")
    void shouldDetectColumnDifferences() {
        TableDefinition baseTable = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("name").type("VARCHAR(255)").nullable(true).build())
                .addColumn(ColumnDefinition.builder("legacy_col").type("VARCHAR(50)").build())
                .primaryKeyColumns(List.of("id"))
                .build();

        TableDefinition targetTable = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("name").type("VARCHAR(255)").nullable(false).defaultValue("'Anonymous'").build()) // nullability + default change
                .addColumn(ColumnDefinition.builder("age").type("INTEGER").nullable(true).build()) // added column
                .primaryKeyColumns(List.of("id"))
                .build();

        DatabaseSchema baseline = DatabaseSchema.builder().addTable(baseTable).build();
        DatabaseSchema target = DatabaseSchema.builder().addTable(targetTable).build();

        SchemaDiff diff = SchemaDiffer.diff(baseline, target);

        assertThat(diff.alteredTables()).hasSize(1);
        TableDiff tableDiff = diff.alteredTables().get(0);

        assertThat(tableDiff.tableName()).isEqualTo("users");
        assertThat(tableDiff.addedColumns()).extracting(ColumnDefinition::name).containsExactly("age");
        assertThat(tableDiff.droppedColumns()).extracting(ColumnDefinition::name).containsExactly("legacy_col");

        assertThat(tableDiff.alteredColumns()).hasSize(1);
        ColumnDiff colDiff = tableDiff.alteredColumns().get(0);
        assertThat(colDiff.columnName()).isEqualTo("name");
        assertThat(colDiff.nullabilityChanged()).isTrue();
        assertThat(colDiff.defaultValueChanged()).isTrue();
        assertThat(colDiff.typeChanged()).isFalse();
    }

    @Test
    @DisplayName("Should detect new and dropped foreign keys, indexes, and check constraints")
    void shouldDetectConstraintAndIndexDifferences() {
        TableDefinition baseTable = TableDefinition.builder("posts")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("user_id").type("INTEGER").build())
                .addIndex(IndexMetadata.builder("idx_old").fields("user_id").type(IndexType.BTREE).build())
                .build();

        TableDefinition targetTable = TableDefinition.builder("posts")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("user_id").type("INTEGER").build())
                .addForeignKey(ForeignKeyDefinition.builder("fk_posts_user")
                        .sourceTable("posts")
                        .sourceColumns(List.of("user_id"))
                        .targetTable("users")
                        .targetColumns(List.of("id"))
                        .onDelete(ReferentialAction.CASCADE)
                        .build())
                .addIndex(IndexMetadata.builder("idx_posts_user_id").fields("user_id").type(IndexType.HASH).build())
                .addCheckConstraint(new CheckConstraintMetadata("chk_user_id", "user_id > 0"))
                .build();

        DatabaseSchema baseline = DatabaseSchema.builder().addTable(baseTable).build();
        DatabaseSchema target = DatabaseSchema.builder().addTable(targetTable).build();

        SchemaDiff diff = SchemaDiffer.diff(baseline, target);

        assertThat(diff.alteredTables()).hasSize(1);
        TableDiff tableDiff = diff.alteredTables().get(0);

        assertThat(tableDiff.addedForeignKeys()).hasSize(1);
        assertThat(tableDiff.addedForeignKeys().get(0).name()).isEqualTo("fk_posts_user");

        assertThat(tableDiff.addedIndexes()).hasSize(1);
        assertThat(tableDiff.addedIndexes().get(0).name()).isEqualTo("idx_posts_user_id");
        assertThat(tableDiff.droppedIndexNames()).containsExactly("idx_old");

        assertThat(tableDiff.addedCheckConstraints()).hasSize(1);
        assertThat(tableDiff.addedCheckConstraints().get(0).name()).isEqualTo("chk_user_id");
    }

    @Test
    @DisplayName("Should detect enum additions, drops, and newly added enum values")
    void shouldDetectEnumDifferences() {
        DatabaseSchema baseline = DatabaseSchema.builder()
                .addEnum("Status", List.of("ACTIVE", "INACTIVE"))
                .addEnum("OldEnum", List.of("A", "B"))
                .build();

        DatabaseSchema target = DatabaseSchema.builder()
                .addEnum("Status", List.of("ACTIVE", "INACTIVE", "ARCHIVED"))
                .addEnum("Role", List.of("USER", "ADMIN"))
                .build();

        SchemaDiff diff = SchemaDiffer.diff(baseline, target);

        assertThat(diff.createdEnums()).containsKey("Role");
        assertThat(diff.createdEnums().get("Role")).containsExactly("USER", "ADMIN");

        assertThat(diff.droppedEnums()).containsExactly("OldEnum");

        assertThat(diff.alteredEnums()).hasSize(1);
        EnumDiff enumDiff = diff.alteredEnums().get(0);
        assertThat(enumDiff.enumName()).isEqualTo("Status");
        assertThat(enumDiff.addedValues()).containsExactly("ARCHIVED");
    }
}
