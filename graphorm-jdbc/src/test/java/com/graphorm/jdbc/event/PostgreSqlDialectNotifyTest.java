package com.graphorm.jdbc.event;

import com.graphorm.jdbc.dialect.PostgreSqlDialect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostgreSqlDialectNotifyTest {

    private final PostgreSqlDialect dialect = new PostgreSqlDialect();

    @Test
    @DisplayName("formatNotifySql formats NOTIFY command correctly with channel and payload")
    void testFormatNotifySql() {
        String sql = dialect.formatNotifySql("graphorm_events", "{\"entity\":\"User\",\"id\":123}");
        assertThat(sql).isEqualTo("NOTIFY graphorm_events, '{\"entity\":\"User\",\"id\":123}';");
    }

    @Test
    @DisplayName("formatNotifySql escapes single quotes in JSON payload")
    void testFormatNotifySqlEscapesSingleQuotes() {
        String payload = "{\"name\":\"O'Connor\"}";
        String sql = dialect.formatNotifySql("graphorm_events", payload);
        assertThat(sql).isEqualTo("NOTIFY graphorm_events, '{\"name\":\"O''Connor\"}';");
    }

    @Test
    @DisplayName("formatNotifySql throws on null channel or payload")
    void testFormatNotifySqlValidation() {
        assertThatThrownBy(() -> dialect.formatNotifySql(null, "{}"))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> dialect.formatNotifySql("channel", null))
                .isInstanceOf(NullPointerException.class);
    }
}
