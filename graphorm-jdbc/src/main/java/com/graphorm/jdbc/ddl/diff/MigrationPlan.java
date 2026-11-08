package com.graphorm.jdbc.ddl.diff;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;
import com.graphorm.jdbc.dialect.SqlDialect;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates an ordered list of executable SQL DDL statements from a {@link SchemaDiff}.
 */
public final class MigrationPlan {

    private MigrationPlan() {}

    /**
     * Translates a schema diff into an ordered, executable SQL statement script.
     *
     * @param diff    the calculated schema diff
     * @param dialect the target SQL dialect
     * @return ordered list of SQL statements
     */
    public static List<String> generateSql(SchemaDiff diff, SqlDialect dialect) {
        Objects.requireNonNull(diff, "SchemaDiff must not be null");
        Objects.requireNonNull(dialect, "SqlDialect must not be null");

        List<String> statements = new ArrayList<>();

        if (diff.isEmpty()) {
            return statements;
        }

        // 1. Create new Enums
        for (Map.Entry<String, List<String>> enumEntry : diff.createdEnums().entrySet()) {
            statements.add(dialect.buildCreateEnumSql(enumEntry.getKey(), enumEntry.getValue()));
        }

        // 2. Alter Enums (add new values)
        for (EnumDiff enumDiff : diff.alteredEnums()) {
            for (String val : enumDiff.addedValues()) {
                statements.add(dialect.buildAddEnumValueSql(enumDiff.enumName(), val));
            }
        }

        // 3. Create new Tables and secondary indexes
        for (TableDefinition table : diff.createdTables()) {
            statements.add(dialect.buildCreateTableSql(table));

            // Secondary indexes on newly created table
            for (IndexMetadata index : table.indexes()) {
                statements.add(dialect.buildCreateIndexSql(index, table.name()));
            }
        }

        // Foreign keys on newly created tables (emitted after ALL tables are created to prevent ordering issues)
        for (TableDefinition table : diff.createdTables()) {
            for (ForeignKeyDefinition fk : table.foreignKeys()) {
                statements.add(dialect.buildAddForeignKeySql(fk));
            }
        }

        // 4. Altered Tables: Drops (constraints/indexes/FKs that might conflict)
        for (TableDiff tableDiff : diff.alteredTables()) {
            String tableName = tableDiff.tableName();

            for (String fkName : tableDiff.droppedForeignKeyNames()) {
                statements.add(dialect.buildDropConstraintSql(tableName, fkName));
            }
            for (String idxName : tableDiff.droppedIndexNames()) {
                statements.add(dialect.buildDropIndexSql(idxName));
            }
            for (String uqName : tableDiff.droppedUniqueConstraintNames()) {
                statements.add(dialect.buildDropConstraintSql(tableName, uqName));
            }
            for (String chkName : tableDiff.droppedCheckConstraintNames()) {
                statements.add(dialect.buildDropConstraintSql(tableName, chkName));
            }
        }

        // 5. Altered Tables: Add/Alter Columns
        for (TableDiff tableDiff : diff.alteredTables()) {
            String tableName = tableDiff.tableName();

            for (ColumnDefinition col : tableDiff.addedColumns()) {
                statements.add(dialect.buildAddColumnSql(tableName, col));
            }

            for (ColumnDiff colDiff : tableDiff.alteredColumns()) {
                if (colDiff.typeChanged()) {
                    statements.add(dialect.buildAlterColumnTypeSql(tableName, colDiff.columnName(), colDiff.newColumn().type()));
                }
                if (colDiff.nullabilityChanged()) {
                    statements.add(dialect.buildAlterColumnNullabilitySql(tableName, colDiff.columnName(), colDiff.newColumn().isNullable()));
                }
                if (colDiff.defaultValueChanged()) {
                    if (colDiff.newColumn().defaultValue() == null || colDiff.newColumn().defaultValue().isBlank()) {
                        statements.add(dialect.buildDropColumnDefaultSql(tableName, colDiff.columnName()));
                    } else {
                        statements.add(dialect.buildAlterColumnDefaultSql(tableName, colDiff.columnName(), colDiff.newColumn().defaultValue()));
                    }
                }
            }
        }

        // 6. Altered Tables: Add Constraints and Indexes
        for (TableDiff tableDiff : diff.alteredTables()) {
            String tableName = tableDiff.tableName();

            for (List<String> uqCols : tableDiff.addedUniqueConstraints()) {
                String uqName = "uq_" + tableName + "_" + String.join("_", uqCols);
                statements.add(dialect.buildAddUniqueConstraintSql(tableName, uqName, uqCols));
            }

            for (CheckConstraintMetadata chk : tableDiff.addedCheckConstraints()) {
                statements.add(dialect.buildAddCheckConstraintSql(tableName, chk));
            }

            for (IndexMetadata idx : tableDiff.addedIndexes()) {
                statements.add(dialect.buildCreateIndexSql(idx, tableName));
            }

            for (ForeignKeyDefinition fk : tableDiff.addedForeignKeys()) {
                statements.add(dialect.buildAddForeignKeySql(fk));
            }
        }

        // 7. Altered Tables: Drop Columns
        for (TableDiff tableDiff : diff.alteredTables()) {
            String tableName = tableDiff.tableName();
            for (ColumnDefinition col : tableDiff.droppedColumns()) {
                statements.add(dialect.buildDropColumnSql(tableName, col.name()));
            }
        }

        // 8. Dropped Tables
        for (TableDefinition table : diff.droppedTables()) {
            statements.add(dialect.buildDropTableSql(table.name()));
        }

        // 9. Dropped Enums
        for (String enumName : diff.droppedEnums()) {
            statements.add(dialect.buildDropEnumSql(enumName));
        }

        return statements;
    }
}
