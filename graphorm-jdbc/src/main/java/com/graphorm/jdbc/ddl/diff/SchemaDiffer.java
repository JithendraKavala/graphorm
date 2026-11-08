package com.graphorm.jdbc.ddl.diff;

import com.graphorm.core.metadata.CheckConstraintMetadata;
import com.graphorm.core.metadata.IndexMetadata;
import com.graphorm.jdbc.ddl.ColumnDefinition;
import com.graphorm.jdbc.ddl.DatabaseSchema;
import com.graphorm.jdbc.ddl.ForeignKeyDefinition;
import com.graphorm.jdbc.ddl.TableDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Computes the semantic diff between a baseline {@link DatabaseSchema} and a desired target {@link DatabaseSchema}.
 */
public final class SchemaDiffer {

    private SchemaDiffer() {}

    /**
     * Diffs two database schemas.
     *
     * @param baseline current database schema snapshot (or empty if creating from scratch)
     * @param target   desired database schema snapshot (from schema DSL / ModelRegistry)
     * @return the computed schema diff
     */
    public static SchemaDiff diff(DatabaseSchema baseline, DatabaseSchema target) {
        Objects.requireNonNull(target, "Target schema must not be null");
        if (baseline == null) {
            baseline = new DatabaseSchema(target.schemaName(), Map.of(), Map.of());
        }

        SchemaDiff.Builder diffBuilder = SchemaDiff.builder();

        // 1. Enums
        diffEnums(baseline, target, diffBuilder);

        // 2. Tables
        diffTables(baseline, target, diffBuilder);

        return diffBuilder.build();
    }

    private static void diffEnums(DatabaseSchema baseline, DatabaseSchema target, SchemaDiff.Builder diffBuilder) {
        for (Map.Entry<String, List<String>> targetEnum : target.enums().entrySet()) {
            String enumName = targetEnum.getKey();
            List<String> targetValues = targetEnum.getValue();

            Optional<List<String>> baselineValuesOpt = baseline.findEnum(enumName);
            if (baselineValuesOpt.isEmpty()) {
                diffBuilder.addCreatedEnum(enumName, targetValues);
            } else {
                List<String> baselineValues = baselineValuesOpt.get();
                List<String> added = new ArrayList<>();
                for (String val : targetValues) {
                    if (!baselineValues.contains(val)) {
                        added.add(val);
                    }
                }
                if (!added.isEmpty()) {
                    diffBuilder.addAlteredEnum(new EnumDiff(enumName, added));
                }
            }
        }

        for (String baselineEnumName : baseline.enums().keySet()) {
            if (target.findEnum(baselineEnumName).isEmpty()) {
                diffBuilder.addDroppedEnum(baselineEnumName);
            }
        }
    }

    private static void diffTables(DatabaseSchema baseline, DatabaseSchema target, SchemaDiff.Builder diffBuilder) {
        for (Map.Entry<String, TableDefinition> targetEntry : target.tables().entrySet()) {
            String tableName = targetEntry.getKey();
            TableDefinition targetTable = targetEntry.getValue();

            Optional<TableDefinition> baselineTableOpt = baseline.findTable(tableName);
            if (baselineTableOpt.isEmpty()) {
                diffBuilder.addCreatedTable(targetTable);
            } else {
                TableDefinition baselineTable = baselineTableOpt.get();
                TableDiff tableDiff = diffSingleTable(baselineTable, targetTable);
                if (!tableDiff.isEmpty()) {
                    diffBuilder.addAlteredTable(tableDiff);
                }
            }
        }

        for (Map.Entry<String, TableDefinition> baselineEntry : baseline.tables().entrySet()) {
            String tableName = baselineEntry.getKey();
            if (target.findTable(tableName).isEmpty()) {
                diffBuilder.addDroppedTable(baselineEntry.getValue());
            }
        }
    }

    private static TableDiff diffSingleTable(TableDefinition baselineTable, TableDefinition targetTable) {
        TableDiff.Builder diff = TableDiff.builder(targetTable.name());

        // 1. Columns
        for (ColumnDefinition targetCol : targetTable.columns()) {
            Optional<ColumnDefinition> baselineColOpt = baselineTable.findColumn(targetCol.name());
            if (baselineColOpt.isEmpty()) {
                diff.addColumn(targetCol);
            } else {
                ColumnDefinition baselineCol = baselineColOpt.get();
                boolean typeChanged = !areTypesEquivalent(baselineCol.type(), targetCol.type());
                boolean nullChanged = (baselineCol.isNullable() != targetCol.isNullable());
                boolean defaultChanged = !areDefaultsEquivalent(baselineCol.defaultValue(), targetCol.defaultValue());

                if (typeChanged || nullChanged || defaultChanged) {
                    diff.alterColumn(new ColumnDiff(
                            targetCol.name(),
                            baselineCol,
                            targetCol,
                            typeChanged,
                            nullChanged,
                            defaultChanged
                    ));
                }
            }
        }

        for (ColumnDefinition baselineCol : baselineTable.columns()) {
            if (targetTable.findColumn(baselineCol.name()).isEmpty()) {
                diff.dropColumn(baselineCol);
            }
        }

        // 2. Foreign Keys
        for (ForeignKeyDefinition targetFk : targetTable.foreignKeys()) {
            boolean matches = baselineTable.foreignKeys().stream().anyMatch(baseFk ->
                    baseFk.targetTable().equalsIgnoreCase(targetFk.targetTable())
                            && baseFk.sourceColumns().equals(targetFk.sourceColumns())
                            && baseFk.targetColumns().equals(targetFk.targetColumns())
                            && baseFk.onDelete() == targetFk.onDelete()
                            && baseFk.onUpdate() == targetFk.onUpdate()
            );
            if (!matches) {
                diff.addForeignKey(targetFk);
            }
        }

        for (ForeignKeyDefinition baseFk : baselineTable.foreignKeys()) {
            boolean matches = targetTable.foreignKeys().stream().anyMatch(targetFk ->
                    targetFk.targetTable().equalsIgnoreCase(baseFk.targetTable())
                            && targetFk.sourceColumns().equals(baseFk.sourceColumns())
                            && targetFk.targetColumns().equals(baseFk.targetColumns())
                            && targetFk.onDelete() == baseFk.onDelete()
                            && targetFk.onUpdate() == baseFk.onUpdate()
            );
            if (!matches) {
                diff.dropForeignKey(baseFk.name());
            }
        }

        // 3. Unique Constraints
        for (List<String> targetUq : targetTable.uniqueConstraints()) {
            boolean matches = baselineTable.uniqueConstraints().stream()
                    .anyMatch(baseUq -> areColumnListsEqual(baseUq, targetUq));
            if (!matches) {
                diff.addUniqueConstraint(targetUq);
            }
        }

        for (List<String> baseUq : baselineTable.uniqueConstraints()) {
            boolean matches = targetTable.uniqueConstraints().stream()
                    .anyMatch(targetUq -> areColumnListsEqual(targetUq, baseUq));
            if (!matches) {
                String uqName = "uq_" + baselineTable.name() + "_" + String.join("_", baseUq);
                diff.dropUniqueConstraint(uqName);
            }
        }

        // 4. Secondary Indexes
        for (IndexMetadata targetIdx : targetTable.indexes()) {
            boolean matches = baselineTable.indexes().stream().anyMatch(baseIdx ->
                    (baseIdx.name().equalsIgnoreCase(targetIdx.name()) || areColumnListsEqual(baseIdx.fields(), targetIdx.fields()))
                            && baseIdx.isUnique() == targetIdx.isUnique()
                            && baseIdx.type() == targetIdx.type()
            );
            if (!matches) {
                diff.addIndex(targetIdx);
            }
        }

        for (IndexMetadata baseIdx : baselineTable.indexes()) {
            boolean matches = targetTable.indexes().stream().anyMatch(targetIdx ->
                    (targetIdx.name().equalsIgnoreCase(baseIdx.name()) || areColumnListsEqual(targetIdx.fields(), baseIdx.fields()))
                            && targetIdx.isUnique() == baseIdx.isUnique()
                            && targetIdx.type() == baseIdx.type()
            );
            if (!matches) {
                diff.dropIndex(baseIdx.name());
            }
        }

        // 5. Check Constraints
        for (CheckConstraintMetadata targetChk : targetTable.checkConstraints()) {
            boolean matches = baselineTable.checkConstraints().stream()
                    .anyMatch(baseChk -> baseChk.name().equalsIgnoreCase(targetChk.name()));
            if (!matches) {
                diff.addCheckConstraint(targetChk);
            }
        }

        for (CheckConstraintMetadata baseChk : baselineTable.checkConstraints()) {
            boolean matches = targetTable.checkConstraints().stream()
                    .anyMatch(targetChk -> targetChk.name().equalsIgnoreCase(baseChk.name()));
            if (!matches) {
                diff.dropCheckConstraint(baseChk.name());
            }
        }

        return diff.build();
    }

    private static boolean areColumnListsEqual(List<String> l1, List<String> l2) {
        if (l1 == null || l2 == null || l1.size() != l2.size()) {
            return false;
        }
        for (int i = 0; i < l1.size(); i++) {
            if (!l1.get(i).equalsIgnoreCase(l2.get(i))) {
                return false;
            }
        }
        return true;
    }

    public static boolean areTypesEquivalent(String t1, String t2) {
        if (t1 == null && t2 == null) return true;
        if (t1 == null || t2 == null) return false;
        String n1 = normalizeType(t1);
        String n2 = normalizeType(t2);
        return n1.equals(n2);
    }

    private static String normalizeType(String type) {
        String s = type.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        return switch (s) {
            case "INT", "INT4", "INTEGER" -> "INTEGER";
            case "BIGINT", "INT8" -> "BIGINT";
            case "SMALLINT", "INT2" -> "SMALLINT";
            case "BOOL", "BOOLEAN" -> "BOOLEAN";
            case "TIMESTAMPTZ", "TIMESTAMPWITHTIMEZONE" -> "TIMESTAMPTZ";
            case "TIMESTAMP", "TIMESTAMPWITHOUTTIMEZONE" -> "TIMESTAMP";
            case "FLOAT8", "DOUBLEPRECISION" -> "DOUBLEPRECISION";
            case "FLOAT4", "REAL" -> "REAL";
            case "VARCHAR", "VARCHAR(255)", "CHARACTERVARYING", "CHARACTERVARYING(255)" -> "VARCHAR(255)";
            case "JSON", "JSONB" -> "JSONB";
            default -> s;
        };
    }

    public static boolean areDefaultsEquivalent(String d1, String d2) {
        if ((d1 == null || d1.isBlank()) && (d2 == null || d2.isBlank())) {
            return true;
        }
        if (d1 == null || d2 == null) {
            return false;
        }
        String clean1 = cleanDefault(d1);
        String clean2 = cleanDefault(d2);
        return clean1.equalsIgnoreCase(clean2);
    }

    private static String cleanDefault(String d) {
        String s = d.trim();
        int castIdx = s.indexOf("::");
        if (castIdx > 0 && !s.startsWith("nextval(")) {
            s = s.substring(0, castIdx).trim();
        }
        if (s.startsWith("(") && s.endsWith(")") && s.length() >= 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.equalsIgnoreCase("now()") || s.equalsIgnoreCase("CURRENT_TIMESTAMP")) {
            return "CURRENT_TIMESTAMP";
        }
        return s;
    }
}
