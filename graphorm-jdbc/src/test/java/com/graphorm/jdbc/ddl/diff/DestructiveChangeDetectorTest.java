package com.graphorm.jdbc.ddl.diff;

import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.DatabaseSchema;
import com.graphorm.jdbc.ddl.TableDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DestructiveChangeDetectorTest {

    @Test
    @DisplayName("Should detect table drops, column drops, type changes, and nullable to not-null")
    void shouldDetectDestructiveChanges() {
        TableDefinition baseUsers = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("data").type("VARCHAR(255)").nullable(true).build())
                .addColumn(ColumnDefinition.builder("dropped_col").type("INTEGER").build())
                .build();

        TableDefinition baseOrders = TableDefinition.builder("orders")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .build();

        DatabaseSchema baseline = DatabaseSchema.builder()
                .addTable(baseUsers)
                .addTable(baseOrders)
                .addEnum("OldEnum", List.of("A", "B"))
                .build();

        TableDefinition targetUsers = TableDefinition.builder("users")
                .addColumn(ColumnDefinition.builder("id").type("INTEGER").primaryKey(true).build())
                .addColumn(ColumnDefinition.builder("data").type("INTEGER").nullable(false).build()) // type change & nullable->not null
                .build();

        DatabaseSchema target = DatabaseSchema.builder()
                .addTable(targetUsers)
                .build();

        SchemaDiff diff = SchemaDiffer.diff(baseline, target);
        List<DestructiveChange> destructive = DestructiveChangeDetector.detect(diff);

        assertThat(destructive).isNotEmpty();
        assertThat(destructive).extracting(DestructiveChange::type).contains(
                DestructiveChange.DestructiveType.DROP_TABLE,
                DestructiveChange.DestructiveType.DROP_COLUMN,
                DestructiveChange.DestructiveType.DROP_ENUM,
                DestructiveChange.DestructiveType.COLUMN_TYPE_CHANGE,
                DestructiveChange.DestructiveType.NULLABLE_TO_NOT_NULL
        );
    }
}
