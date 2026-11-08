package com.graphorm.cli.introspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnhancedIntrospectionTest {

    @Test
    @DisplayName("SchemaEmitter emits enum blocks and maps columns to enum types")
    void emitsEnumsAndMapsColumnTypes() {
        EnumInfo userRole = new EnumInfo("user_role", List.of("ADMIN", "MEMBER", "GUEST"));

        TableInfo userTable = new TableInfo("users", false);
        userTable.addColumn(new ColumnInfo("users", "id", "text", "text", false, null, 1));
        userTable.addColumn(new ColumnInfo("users", "role", "USER-DEFINED", "user_role", false, null, 2));
        userTable.addPrimaryKeyColumn("id");

        String schema = SchemaEmitter.emit(List.of(userTable), List.of(userRole), "postgresql");

        assertThat(schema).contains("enum UserRole {");
        assertThat(schema).contains("  ADMIN");
        assertThat(schema).contains("  MEMBER");
        assertThat(schema).contains("  GUEST");
        assertThat(schema).contains("  @@map(\"user_role\")");
        assertThat(schema).contains("role  UserRole");
    }

    @Test
    @DisplayName("SchemaEmitter emits native types for varchar, decimal, char, text")
    void emitsNativeTypes() {
        TableInfo table = new TableInfo("products", false);
        table.addColumn(new ColumnInfo("products", "id", "text", "text", false, null, 1));
        table.addColumn(new ColumnInfo("products", "code", "character varying", "varchar", false, null, 2, 50, null, null));
        table.addColumn(new ColumnInfo("products", "sku", "character", "bpchar", false, null, 3, 8, null, null));
        table.addColumn(new ColumnInfo("products", "price", "numeric", "numeric", false, null, 4, null, 10, 2));
        table.addColumn(new ColumnInfo("products", "description", "text", "text", true, null, 5));
        table.addPrimaryKeyColumn("id");

        String schema = SchemaEmitter.emit(List.of(table), List.of(), "postgresql");

        assertThat(schema).contains("@db.VarChar(50)");
        assertThat(schema).contains("@db.Char(8)");
        assertThat(schema).contains("@db.Decimal(10, 2)");
        assertThat(schema).contains("@db.Text");
    }

    @Test
    @DisplayName("SchemaEmitter emits multi-column unique constraints, multi-column indexes, and check constraints")
    void emitsMultiColumnConstraintsAndIndexes() {
        TableInfo table = new TableInfo("order_items", false);
        table.addColumn(new ColumnInfo("order_items", "order_id", "text", "text", false, null, 1));
        table.addColumn(new ColumnInfo("order_items", "item_id", "text", "text", false, null, 2));
        table.addColumn(new ColumnInfo("order_items", "quantity", "integer", "int4", false, null, 3));
        table.addPrimaryKeyColumn("order_id");
        table.addPrimaryKeyColumn("item_id");

        table.addUniqueConstraint(List.of("order_id", "item_id"));
        table.addIndex(new IndexInfo("idx_order_qty", List.of("order_id", "quantity"), false, "BTREE"));
        table.addCheckConstraint(new CheckConstraintInfo("positive_qty", "quantity > 0"));

        String schema = SchemaEmitter.emit(List.of(table), List.of(), "postgresql");

        assertThat(schema).contains("@@id([orderId, itemId])");
        assertThat(schema).contains("@@unique([orderId, itemId])");
        assertThat(schema).contains("@@index([orderId, quantity])");
        assertThat(schema).contains("@@check(\"quantity > 0\")");
    }

    @Test
    @DisplayName("SchemaEmitter emits foreign key referential actions (onDelete, onUpdate)")
    void emitsReferentialActions() {
        TableInfo deptTable = new TableInfo("departments", false);
        deptTable.addColumn(new ColumnInfo("departments", "id", "text", "text", false, null, 1));
        deptTable.addPrimaryKeyColumn("id");

        TableInfo empTable = new TableInfo("employees", false);
        empTable.addColumn(new ColumnInfo("employees", "id", "text", "text", false, null, 1));
        empTable.addColumn(new ColumnInfo("employees", "department_id", "text", "text", true, null, 2));
        empTable.addPrimaryKeyColumn("id");
        empTable.addForeignKey(new ForeignKeyInfo("fk_emp_dept", "employees", "department_id", "departments", "id", "CASCADE", "SET NULL"));

        String schema = SchemaEmitter.emit(List.of(deptTable, empTable), List.of(), "postgresql");

        assertThat(schema).contains("onDelete: Cascade");
        assertThat(schema).contains("onUpdate: SetNull");
    }
}
