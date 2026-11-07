package com.graphorm.cli.introspect;

import java.util.*;

/**
 * Metadata about an introspected table or database view.
 */
public final class TableInfo {

    private final String tableName;
    private final boolean isView;
    private final List<ColumnInfo> columns = new ArrayList<>();
    private final List<String> primaryKeyColumns = new ArrayList<>();
    private final Set<String> uniqueColumns = new LinkedHashSet<>();
    private final List<ForeignKeyInfo> foreignKeys = new ArrayList<>();

    public TableInfo(String tableName, boolean isView) {
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.isView = isView;
    }

    public String tableName() {
        return tableName;
    }

    public boolean isView() {
        return isView;
    }

    public List<ColumnInfo> columns() {
        return Collections.unmodifiableList(columns);
    }

    public void addColumn(ColumnInfo col) {
        this.columns.add(Objects.requireNonNull(col));
    }

    public List<String> primaryKeyColumns() {
        return Collections.unmodifiableList(primaryKeyColumns);
    }

    public void addPrimaryKeyColumn(String col) {
        if (!this.primaryKeyColumns.contains(col)) {
            this.primaryKeyColumns.add(col);
        }
    }

    public Set<String> uniqueColumns() {
        return Collections.unmodifiableSet(uniqueColumns);
    }

    public void addUniqueColumn(String col) {
        this.uniqueColumns.add(col);
    }

    public List<ForeignKeyInfo> foreignKeys() {
        return Collections.unmodifiableList(foreignKeys);
    }

    public void addForeignKey(ForeignKeyInfo fk) {
        this.foreignKeys.add(Objects.requireNonNull(fk));
    }
}
