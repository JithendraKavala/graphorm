package com.graphorm.core.metadata;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultValue Unit Tests")
class DefaultValueTest {

    @Test
    @DisplayName("Should create and verify StringValue")
    void shouldCreateStringValue() {
        DefaultValue str = DefaultValue.ofString("ACTIVE");
        assertThat(str).isInstanceOf(DefaultValue.StringValue.class);
        assertThat(str.asString()).isEqualTo("ACTIVE");
        assertThat(((DefaultValue.StringValue) str).value()).isEqualTo("ACTIVE");

        DefaultValue empty = DefaultValue.ofString("");
        assertThat(empty.asString()).isEqualTo("");

        assertThatThrownBy(() -> new DefaultValue.StringValue(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should create and verify NumericValue")
    void shouldCreateNumericValue() {
        DefaultValue num1 = DefaultValue.ofNumeric("0");
        assertThat(num1.asString()).isEqualTo("0");

        DefaultValue num2 = DefaultValue.ofNumeric(42L);
        assertThat(num2.asString()).isEqualTo("42");

        DefaultValue num3 = DefaultValue.ofNumeric(9.99);
        assertThat(num3.asString()).isEqualTo("9.99");

        DefaultValue neg = DefaultValue.ofNumeric("-5");
        assertThat(neg.asString()).isEqualTo("-5");

        assertThatThrownBy(() -> new DefaultValue.NumericValue(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should create and verify BooleanValue")
    void shouldCreateBooleanValue() {
        DefaultValue bTrue = DefaultValue.ofBoolean(true);
        assertThat(bTrue.asString()).isEqualTo("true");
        assertThat(((DefaultValue.BooleanValue) bTrue).value()).isTrue();

        DefaultValue bFalse = DefaultValue.ofBoolean(false);
        assertThat(bFalse.asString()).isEqualTo("false");
        assertThat(((DefaultValue.BooleanValue) bFalse).value()).isFalse();
    }

    @Test
    @DisplayName("Should create and verify NullValue")
    void shouldCreateNullValue() {
        DefaultValue nullVal = DefaultValue.ofNull();
        assertThat(nullVal).isInstanceOf(DefaultValue.NullValue.class);
        assertThat(nullVal.asString()).isEqualTo("NULL");
    }

    @Test
    @DisplayName("Should create and verify ExpressionValue")
    void shouldCreateExpressionValue() {
        DefaultValue expr = DefaultValue.ofExpression("now()");
        assertThat(expr).isInstanceOf(DefaultValue.ExpressionValue.class);
        assertThat(expr.asString()).isEqualTo("now()");

        DefaultValue ts = DefaultValue.ofExpression("CURRENT_TIMESTAMP");
        assertThat(ts.asString()).isEqualTo("CURRENT_TIMESTAMP");

        DefaultValue auto = DefaultValue.ofExpression("autoincrement()");
        assertThat(auto.asString()).isEqualTo("autoincrement()");

        assertThatThrownBy(() -> new DefaultValue.ExpressionValue(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should infer DefaultValue from raw strings and types via DefaultValue.from")
    void shouldInferDefaultValueFrom() {
        assertThat(DefaultValue.from(null, null)).isNull();
        assertThat(DefaultValue.from("null", null)).isInstanceOf(DefaultValue.NullValue.class);
        assertThat(DefaultValue.from("NULL", String.class)).isInstanceOf(DefaultValue.NullValue.class);

        assertThat(DefaultValue.from("true", Boolean.class)).isEqualTo(new DefaultValue.BooleanValue(true));
        assertThat(DefaultValue.from("false", null)).isEqualTo(new DefaultValue.BooleanValue(false));

        // Already quoted string literal
        assertThat(DefaultValue.from("'ACTIVE'", null)).isEqualTo(new DefaultValue.StringValue("ACTIVE"));
        assertThat(DefaultValue.from("'John''s'", String.class)).isEqualTo(new DefaultValue.StringValue("John's"));

        // Functions and SQL expressions
        assertThat(DefaultValue.from("now()", null)).isEqualTo(new DefaultValue.ExpressionValue("now()"));
        assertThat(DefaultValue.from("gen_random_uuid()", null)).isEqualTo(new DefaultValue.ExpressionValue("gen_random_uuid()"));
        assertThat(DefaultValue.from("CURRENT_TIMESTAMP", null)).isEqualTo(new DefaultValue.ExpressionValue("CURRENT_TIMESTAMP"));
        assertThat(DefaultValue.from("CURRENT_DATE", null)).isEqualTo(new DefaultValue.ExpressionValue("CURRENT_DATE"));

        // Numerics including exponent notation
        assertThat(DefaultValue.from("0", Integer.class)).isEqualTo(new DefaultValue.NumericValue("0"));
        assertThat(DefaultValue.from("9.99", Double.class)).isEqualTo(new DefaultValue.NumericValue("9.99"));
        assertThat(DefaultValue.from("-5", null, "Int")).isEqualTo(new DefaultValue.NumericValue("-5"));
        assertThat(DefaultValue.from("1.5e-3", null)).isEqualTo(new DefaultValue.NumericValue("1.5e-3"));
        assertThat(DefaultValue.from("1e5", null)).isEqualTo(new DefaultValue.NumericValue("1e5"));
        assertThat(DefaultValue.from("-3.14e-2", Double.class)).isEqualTo(new DefaultValue.NumericValue("-3.14e-2"));

        // Strings (including "null", "true", "false" on string types and double quotes)
        assertThat(DefaultValue.from("ACTIVE", String.class)).isEqualTo(new DefaultValue.StringValue("ACTIVE"));
        assertThat(DefaultValue.from("USER", null, "Role")).isEqualTo(new DefaultValue.StringValue("USER"));
        assertThat(DefaultValue.from("null", String.class)).isEqualTo(new DefaultValue.StringValue("null"));
        assertThat(DefaultValue.from("true", String.class)).isEqualTo(new DefaultValue.StringValue("true"));
        assertThat(DefaultValue.from("false", String.class)).isEqualTo(new DefaultValue.StringValue("false"));
        assertThat(DefaultValue.from("\"null\"", null)).isEqualTo(new DefaultValue.StringValue("null"));
        assertThat(DefaultValue.from("\"true\"", null)).isEqualTo(new DefaultValue.StringValue("true"));
    }
}
