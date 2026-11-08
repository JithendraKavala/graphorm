package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FieldMetadata Unit Tests")
class FieldMetadataTest {

    @Test
    @DisplayName("Should create FieldMetadata with default values")
    void shouldCreateFieldMetadataWithDefaults() {
        FieldMetadata field = new FieldMetadata(
                "email",
                String.class,
                "user_email",
                false,
                false,
                true,
                false,
                null
        );

        assertThat(field.name()).isEqualTo("email");
        assertThat(field.javaType()).isEqualTo(String.class);
        assertThat(field.typeName()).isEqualTo("String");
        assertThat(field.dbColumnName()).isEqualTo("user_email");
        assertThat(field.isPrimaryKey()).isFalse();
        assertThat(field.isNullable()).isFalse();
        assertThat(field.isUnique()).isTrue();
        assertThat(field.isGenerated()).isFalse();
        assertThat(field.defaultValue()).isNull();
        assertThat(field.isAggregate()).isFalse();
        assertThat(field.aggregateFunction()).isNull();
        assertThat(field.expression()).isNull();
        assertThat(field.isRelation()).isFalse();
        assertThat(field.relationName()).isNull();
        assertThat(field.hasAuthRoles()).isFalse();
        assertThat(field.authRoles()).isEmpty();
        assertThat(field.isInjected()).isFalse();
        assertThat(field.injectedFrom()).isNull();
    }

    @Test
    @DisplayName("Should default dbColumnName to name when null or blank")
    void shouldDefaultDbColumnNameToName() {
        FieldMetadata field1 = FieldMetadata.builder("username").build();
        assertThat(field1.dbColumnName()).isEqualTo("username");

        FieldMetadata field2 = new FieldMetadata("id", Long.class, "", true, false, true, true, "autoincrement()");
        assertThat(field2.dbColumnName()).isEqualTo("id");
    }

    @Test
    @DisplayName("Should reject null or blank field name")
    void shouldRejectNullOrBlankFieldName() {
        assertThatThrownBy(() -> FieldMetadata.builder(null))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> new FieldMetadata("", String.class, "col", false, true, false, false, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Field name must not be blank");
    }

    @Test
    @DisplayName("Should build aggregate and expression fields")
    void shouldBuildAggregateAndExpressionFields() {
        FieldMetadata aggField = FieldMetadata.builder("totalCredits")
                .javaType(Integer.class)
                .aggregate("SUM")
                .build();

        assertThat(aggField.isAggregate()).isTrue();
        assertThat(aggField.aggregateFunction()).isEqualTo("SUM");

        FieldMetadata exprField = FieldMetadata.builder("gpa")
                .javaType(Double.class)
                .expression("AVG(grade_points)")
                .build();

        assertThat(exprField.expression()).isEqualTo("AVG(grade_points)");
    }

    @Test
    @DisplayName("Should build relational navigation field")
    void shouldBuildRelationalField() {
        FieldMetadata relField = FieldMetadata.builder("department")
                .relation("StudentDepartment")
                .build();

        assertThat(relField.isRelation()).isTrue();
        assertThat(relField.relationName()).isEqualTo("StudentDepartment");
    }

    @Test
    @DisplayName("Should configure and verify auth roles on FieldMetadata")
    void shouldConfigureAndVerifyAuthRoles() {
        FieldMetadata securedField = FieldMetadata.builder("ssn")
                .javaType(String.class)
                .authRoles("ADMIN", "FINANCE")
                .build();

        assertThat(securedField.hasAuthRoles()).isTrue();
        assertThat(securedField.authRoles()).containsExactly("ADMIN", "FINANCE");

        // Verify role authorization
        assertThat(securedField.isAuthorized(List.of("ADMIN"))).isTrue();
        assertThat(securedField.isAuthorized(List.of("ROLE_ADMIN"))).isTrue();
        assertThat(securedField.isAuthorized(List.of("FINANCE"))).isTrue();
        assertThat(securedField.isAuthorized(List.of("ROLE_FINANCE"))).isTrue();
        assertThat(securedField.isAuthorized(List.of("USER", "STUDENT"))).isFalse();
        assertThat(securedField.isAuthorized(List.of())).isFalse();
        assertThat(securedField.isAuthorized(null)).isFalse();

        // Unsecured field permits all
        FieldMetadata openField = FieldMetadata.builder("name")
                .javaType(String.class)
                .build();
        assertThat(openField.hasAuthRoles()).isFalse();
        assertThat(openField.isAuthorized(List.of())).isTrue();
        assertThat(openField.isAuthorized(null)).isTrue();
    }

    @Test
    @DisplayName("Should configure and verify backend-injected field")
    void shouldConfigureBackendInjectedField() {
        FieldMetadata tenantField = FieldMetadata.builder("tenantId", String.class)
                .injected("tenantId")
                .build();

        assertThat(tenantField.isInjected()).isTrue();
        assertThat(tenantField.injectedFrom()).isEqualTo("tenantId");

        FieldMetadata createdByField = FieldMetadata.builder("createdBy", String.class)
                .injected(true, "auth.username")
                .build();

        assertThat(createdByField.isInjected()).isTrue();
        assertThat(createdByField.injectedFrom()).isEqualTo("auth.username");

        FieldMetadata standardField = FieldMetadata.builder("description", String.class).build();
        assertThat(standardField.isInjected()).isFalse();
        assertThat(standardField.injectedFrom()).isNull();
    }

    @Test
    @DisplayName("Should support backward-compatible FieldMetadata constructors")
    void shouldSupportBackwardCompatibleConstructors() {
        FieldMetadata f14 = new FieldMetadata(
                "title",
                String.class,
                "String",
                "title",
                false,
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                false,
                null
        );
        assertThat(f14.isInjected()).isFalse();
        assertThat(f14.injectedFrom()).isNull();
        assertThat(f14.hasAuthRoles()).isFalse();

        FieldMetadata f15 = new FieldMetadata(
                "title2",
                String.class,
                "String",
                "title2",
                false,
                false,
                false,
                false,
                null,
                false,
                null,
                null,
                false,
                null,
                List.of("ADMIN")
        );
        assertThat(f15.isInjected()).isFalse();
        assertThat(f15.injectedFrom()).isNull();
        assertThat(f15.hasAuthRoles()).isTrue();
        assertThat(f15.authRoles()).containsExactly("ADMIN");
    }

    @Test
    @DisplayName("Should satisfy equals, hashCode, and toString")
    void shouldSatisfyRecordContract() {
        FieldMetadata f1 = FieldMetadata.builder("id", Long.class).primaryKey(true).build();
        FieldMetadata f2 = FieldMetadata.builder("id", Long.class).primaryKey(true).build();

        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
        assertThat(f1.toString()).contains("id", "Long");
    }
}
