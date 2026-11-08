package com.graphorm.cli.introspect;

import java.util.*;

/**
 * Emits GraphORM schema DSL from introspected database tables and views.
 */
public final class SchemaEmitter {

    private SchemaEmitter() {}

    /**
     * Emits a complete schema DSL string from the introspected tables.
     *
     * @param tables       list of introspected tables and views
     * @param databaseName database or provider name (e.g. "postgresql")
     * @return formatted GraphORM schema string
     */
    public static String emit(List<TableInfo> tables, String databaseName) {
        StringBuilder sb = new StringBuilder();

        // 1. Datasource block
        sb.append("// GraphORM Schema (Introspected from PostgreSQL)\n");
        sb.append("// Generated at: ").append(java.time.Instant.now()).append("\n\n");
        sb.append("datasource db {\n");
        sb.append("  provider = \"").append(databaseName != null ? databaseName : "postgresql").append("\"\n");
        sb.append("  url      = env(\"DATABASE_URL\")\n");
        sb.append("}\n\n");

        // 2. Generator block
        sb.append("generator client {\n");
        sb.append("  provider = \"graphorm-client-java\"\n");
        sb.append("}\n\n");

        if (tables == null || tables.isEmpty()) {
            return sb.toString();
        }

        // Map table name to TableInfo and model name
        Map<String, TableInfo> tableMap = new LinkedHashMap<>();
        Map<String, String> tableToModelName = new LinkedHashMap<>();
        for (TableInfo t : tables) {
            tableMap.put(t.tableName(), t);
            tableToModelName.put(t.tableName(), toModelName(t.tableName()));
        }

        // Track reverse relationships to add to target models: targetTable -> List of (relationFieldName, sourceType, isList)
        Map<String, List<ReverseRelation>> reverseRelations = new LinkedHashMap<>();
        for (TableInfo t : tables) {
            if (t.isView()) continue;
            for (ForeignKeyInfo fk : t.foreignKeys()) {
                String targetTable = fk.targetTable();
                if (tableMap.containsKey(targetTable)) {
                    String sourceModelName = tableToModelName.get(t.tableName());
                    String revFieldName = toPlural(toCamelCase(sourceModelName));
                    reverseRelations.computeIfAbsent(targetTable, k -> new ArrayList<>())
                            .add(new ReverseRelation(revFieldName, sourceModelName));
                }
            }
        }

        // 3. Emit Models and Views
        for (TableInfo t : tables) {
            String entityName = tableToModelName.get(t.tableName());
            boolean isView = t.isView();

            if (isView) {
                sb.append("view ").append(entityName).append(" {\n");
            } else {
                sb.append("model ").append(entityName).append(" {\n");
            }

            // Keep track of emitted field names to avoid duplicates
            Set<String> fieldNames = new HashSet<>();
            Map<String, String> colToFieldName = new LinkedHashMap<>();

            // Build map of FK by source column
            Map<String, ForeignKeyInfo> fkByColumn = new LinkedHashMap<>();
            for (ForeignKeyInfo fk : t.foreignKeys()) {
                fkByColumn.put(fk.sourceColumn(), fk);
            }

            // Emit columns
            List<ColumnLine> columnLines = new ArrayList<>();
            boolean hasSinglePk = !isView && t.primaryKeyColumns().size() == 1;
            boolean hasCompositePk = !isView && t.primaryKeyColumns().size() > 1;

            // For views, if no primary key, pick the first column or column named "id" as @id
            String viewPkColumn = null;
            if (isView && !t.columns().isEmpty()) {
                for (ColumnInfo c : t.columns()) {
                    if ("id".equalsIgnoreCase(c.columnName())) {
                        viewPkColumn = c.columnName();
                        break;
                    }
                }
                if (viewPkColumn == null) {
                    viewPkColumn = t.columns().get(0).columnName();
                }
            }

            for (ColumnInfo c : t.columns()) {
                String fieldName = toCamelCase(c.columnName());
                if (fieldNames.contains(fieldName)) {
                    fieldName = fieldName + "_" + c.ordinalPosition();
                }
                fieldNames.add(fieldName);
                colToFieldName.put(c.columnName(), fieldName);

                String baseType = mapPostgresType(c.dataType(), c.udtName());
                boolean isPk = hasSinglePk && t.primaryKeyColumns().contains(c.columnName());
                boolean isViewPk = isView && c.columnName().equals(viewPkColumn);
                boolean isNullable = c.isNullable() && !isPk && !isViewPk;
                String fieldType = isNullable ? baseType + "?" : baseType;

                List<String> attrs = new ArrayList<>();
                if (isPk || isViewPk) {
                    attrs.add("@id");
                }

                if (!isView && c.columnDefault() != null) {
                    String def = c.columnDefault().toLowerCase();
                    if (def.contains("nextval(")) {
                        attrs.add("@default(autoincrement())");
                    } else if (def.contains("now()") || def.contains("current_timestamp")) {
                        attrs.add("@default(now())");
                    } else if (def.startsWith("'true'::") || def.equals("true")) {
                        attrs.add("@default(true)");
                    } else if (def.startsWith("'false'::") || def.equals("false")) {
                        attrs.add("@default(false)");
                    }
                }

                if (!isView && !isPk && t.uniqueColumns().contains(c.columnName())) {
                    attrs.add("@unique");
                }

                if (!fieldName.equals(c.columnName())) {
                    attrs.add("@map(\"" + c.columnName() + "\")");
                }

                columnLines.add(new ColumnLine(fieldName, fieldType, String.join(" ", attrs)));
            }

            // Emit relation fields for foreign keys
            List<ColumnLine> relationLines = new ArrayList<>();
            if (!isView) {
                for (ForeignKeyInfo fk : t.foreignKeys()) {
                    String targetTable = fk.targetTable();
                    String targetModel = tableToModelName.getOrDefault(targetTable, toModelName(targetTable));
                    String localFieldName = colToFieldName.getOrDefault(fk.sourceColumn(), toCamelCase(fk.sourceColumn()));
                    String targetFieldName = toCamelCase(fk.targetColumn());

                    // Generate clean relation field name, e.g. "departmentId" -> "department"
                    String relName;
                    if (localFieldName.toLowerCase().endsWith("id") && localFieldName.length() > 2) {
                        relName = localFieldName.substring(0, localFieldName.length() - 2);
                        if (relName.isEmpty()) {
                            relName = toCamelCase(targetModel);
                        }
                    } else {
                        relName = toCamelCase(targetModel);
                    }

                    if (fieldNames.contains(relName)) {
                        relName = relName + "Rel";
                    }
                    fieldNames.add(relName);

                    ColumnInfo sourceCol = t.columns().stream()
                            .filter(c -> c.columnName().equals(fk.sourceColumn()))
                            .findFirst().orElse(null);
                    boolean isFkNullable = sourceCol != null && sourceCol.isNullable();
                    String relType = isFkNullable ? targetModel + "?" : targetModel;

                    StringBuilder relAttr = new StringBuilder(String.format("@relation(fields: [%s], references: [%s]", localFieldName, targetFieldName));
                    if (fk.onDelete() != null && !fk.onDelete().isBlank() && !fk.onDelete().equalsIgnoreCase("NO ACTION")) {
                        relAttr.append(", onDelete: ").append(toPascalCase(fk.onDelete()));
                    }
                    if (fk.onUpdate() != null && !fk.onUpdate().isBlank() && !fk.onUpdate().equalsIgnoreCase("NO ACTION")) {
                        relAttr.append(", onUpdate: ").append(toPascalCase(fk.onUpdate()));
                    }
                    relAttr.append(")");

                    relationLines.add(new ColumnLine(relName, relType, relAttr.toString()));
                }
            }

            // Emit reverse relations
            List<ColumnLine> revRelationLines = new ArrayList<>();
            if (!isView && reverseRelations.containsKey(t.tableName())) {
                for (ReverseRelation rev : reverseRelations.get(t.tableName())) {
                    String revName = rev.fieldName();
                    if (fieldNames.contains(revName)) {
                        revName = revName + "List";
                    }
                    fieldNames.add(revName);
                    revRelationLines.add(new ColumnLine(revName, rev.targetModel() + "[]", ""));
                }
            }

            // Find maximum lengths for formatting padding
            List<ColumnLine> allLines = new ArrayList<>();
            allLines.addAll(columnLines);
            allLines.addAll(relationLines);
            allLines.addAll(revRelationLines);

            int maxNameLen = allLines.stream().mapToInt(l -> l.name().length()).max().orElse(10);
            int maxTypeLen = allLines.stream().mapToInt(l -> l.type().length()).max().orElse(10);

            for (ColumnLine line : columnLines) {
                formatLine(sb, line, maxNameLen, maxTypeLen);
            }

            if (!relationLines.isEmpty() || !revRelationLines.isEmpty()) {
                sb.append("\n");
                for (ColumnLine line : relationLines) {
                    formatLine(sb, line, maxNameLen, maxTypeLen);
                }
                for (ColumnLine line : revRelationLines) {
                    formatLine(sb, line, maxNameLen, maxTypeLen);
                }
            }

            // Block attributes
            List<String> blockAttrs = new ArrayList<>();
            if (isView) {
                blockAttrs.add("@@view");
                blockAttrs.add("@@map(\"" + t.tableName() + "\")");
            } else {
                if (hasCompositePk) {
                    List<String> pkFields = t.primaryKeyColumns().stream()
                            .map(c -> colToFieldName.getOrDefault(c, toCamelCase(c)))
                            .toList();
                    blockAttrs.add("@@id([" + String.join(", ", pkFields) + "])");
                }
                if (!entityName.equals(t.tableName())) {
                    blockAttrs.add("@@map(\"" + t.tableName() + "\")");
                }

                // Indexes
                for (IndexInfo idx : t.indexes()) {
                    List<String> idxFields = idx.columns().stream()
                            .map(c -> colToFieldName.getOrDefault(c, toCamelCase(c)))
                            .toList();
                    StringBuilder idxAttr = new StringBuilder("@@index([" + String.join(", ", idxFields) + "]");
                    if (idx.type() != null && !idx.type().equalsIgnoreCase("BTREE")) {
                        idxAttr.append(", type: ").append(toPascalCase(idx.type()));
                    }
                    idxAttr.append(")");
                    blockAttrs.add(idxAttr.toString());
                }

                // Check constraints
                for (CheckConstraintInfo chk : t.checkConstraints()) {
                    blockAttrs.add("@@check(\"" + chk.expression().replace("\"", "\\\"") + "\")");
                }
            }

            if (!blockAttrs.isEmpty()) {
                sb.append("\n");
                for (String ba : blockAttrs) {
                    sb.append("  ").append(ba).append("\n");
                }
            }

            sb.append("}\n\n");
        }

        return sb.toString();
    }

    private static void formatLine(StringBuilder sb, ColumnLine line, int maxNameLen, int maxTypeLen) {
        sb.append("  ").append(line.name());
        sb.append(" ".repeat(Math.max(1, maxNameLen - line.name().length() + 2)));
        sb.append(line.type());
        if (!line.attributes().isEmpty()) {
            sb.append(" ".repeat(Math.max(1, maxTypeLen - line.type().length() + 2)));
            sb.append(line.attributes());
        }
        sb.append("\n");
    }

    public static String mapPostgresType(String dataType, String udtName) {
        String type = (udtName != null && !udtName.isBlank()) ? udtName.toLowerCase() : dataType.toLowerCase();
        return switch (type) {
            case "int2", "smallint", "int4", "integer", "serial", "smallserial" -> "Int";
            case "int8", "bigint", "bigserial" -> "BigInt";
            case "varchar", "character varying", "text", "char", "character", "bpchar" -> "String";
            case "bool", "boolean" -> "Boolean";
            case "numeric", "decimal" -> "Decimal";
            case "float4", "real", "float8", "double precision" -> "Float";
            case "timestamp", "timestamptz", "timestamp without time zone", "timestamp with time zone" -> "DateTime";
            case "date" -> "Date";
            case "time", "timetz", "time without time zone", "time with time zone" -> "Time";
            case "uuid" -> "UUID";
            case "json", "jsonb" -> "Json";
            case "bytea" -> "Bytes";
            default -> "String";
        };
    }

    public static String toModelName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return "Model";
        }
        String clean = tableName.replaceAll("[^a-zA-Z0-9_]", "");
        String singular = singularize(clean);
        return toPascalCase(singular);
    }

    public static String toPascalCase(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : input.toCharArray()) {
            if (c == '_' || c == '-' || c == ' ') {
                capitalize = true;
            } else if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String toCamelCase(String input) {
        String pascal = toPascalCase(input);
        if (pascal.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    public static String singularize(String name) {
        if (name == null || name.length() <= 1) {
            return name;
        }
        String lower = name.toLowerCase();
        if (lower.endsWith("ies") && name.length() > 3) {
            return name.substring(0, name.length() - 3) + "y";
        }
        if (lower.endsWith("courses")) {
            return name.substring(0, name.length() - 1);
        }
        if (lower.endsWith("databases")) {
            return name.substring(0, name.length() - 1);
        }
        if (lower.endsWith("ses")) {
            if (lower.endsWith("sses")) {
                return name.substring(0, name.length() - 2);
            }
            if (lower.endsWith("rses") || lower.endsWith("uses") || lower.endsWith("ases") || lower.endsWith("ises") || lower.endsWith("oses")) {
                return name.substring(0, name.length() - 1);
            }
            return name.substring(0, name.length() - 2);
        }
        if (lower.endsWith("xes") || lower.endsWith("ches") || lower.endsWith("shes")) {
            return name.substring(0, name.length() - 2);
        }
        if (lower.endsWith("s") && !lower.endsWith("ss") && !lower.endsWith("us") && !lower.endsWith("is")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    public static String toPlural(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        if (name.endsWith("y") && !name.endsWith("ay") && !name.endsWith("ey") && !name.endsWith("oy")) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        if (name.endsWith("s") || name.endsWith("x") || name.endsWith("ch") || name.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    private record ColumnLine(String name, String type, String attributes) {}
    private record ReverseRelation(String fieldName, String targetModel) {}
}
