package com.graphorm.jdbc.ddl.diff;

import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Analyzes a {@link SchemaDiff} and flags potentially destructive operations.
 */
public final class DestructiveChangeDetector {

    private DestructiveChangeDetector() {}

    /**
     * Inspects the schema diff and returns all detected destructive changes.
     *
     * @param diff the schema diff
     * @return list of destructive changes
     */
    public static List<DestructiveChange> detect(SchemaDiff diff) {
        Objects.requireNonNull(diff, "SchemaDiff must not be null");
        List<DestructiveChange> changes = new ArrayList<>();

        // Dropped tables
        for (TableDefinition table : diff.droppedTables()) {
            changes.add(DestructiveChange.dropTable(table.name()));
        }

        // Dropped enums
        for (String enumName : diff.droppedEnums()) {
            changes.add(DestructiveChange.dropEnum(enumName));
        }

        // Altered tables
        for (TableDiff tableDiff : diff.alteredTables()) {
            String tableName = tableDiff.tableName();

            // Dropped columns
            for (ColumnDefinition col : tableDiff.droppedColumns()) {
                changes.add(DestructiveChange.dropColumn(tableName, col.name()));
            }

            // Altered columns
            for (ColumnDiff colDiff : tableDiff.alteredColumns()) {
                if (colDiff.typeChanged()) {
                    changes.add(DestructiveChange.typeChange(
                            tableName,
                            colDiff.columnName(),
                            colDiff.oldColumn().type(),
                            colDiff.newColumn().type()
                    ));
                }
                if (colDiff.nullabilityChanged() && !colDiff.newColumn().isNullable()) {
                    changes.add(DestructiveChange.nullableToNotNull(tableName, colDiff.columnName()));
                }
            }
        }

        return List.copyOf(changes);
    }
}
