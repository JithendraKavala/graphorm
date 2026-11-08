package com.graphorm.jdbc.observability;

import com.graphorm.jdbc.executor.BoundSql;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SqlQueryFingerprinter Tests")
class SqlQueryFingerprinterTest {

    @Test
    @DisplayName("Normalizes string and numeric literals to placeholders")
    void testNormalizesLiterals() {
        String sql = "SELECT id, name, age FROM users WHERE name = 'Alice' AND age > 25 AND score >= 99.5";
        SqlQueryFingerprinter.FingerprintResult result = SqlQueryFingerprinter.fingerprint(sql);

        assertThat(result.normalizedSql()).isEqualTo("SELECT id, name, age FROM users WHERE name = ? AND age > ? AND score >= ?");
        assertThat(result.operation()).isEqualTo("SELECT");
        assertThat(result.targetTable()).isEqualTo("users");
        assertThat(result.fingerprint()).hasSize(64);
    }

    @Test
    @DisplayName("Collapses IN-lists with multiple parameters")
    void testCollapsesInLists() {
        String sql1 = "SELECT * FROM orders WHERE status IN ('PENDING', 'PROCESSING', 'SHIPPED')";
        String sql2 = "SELECT * FROM orders WHERE status IN ('DELIVERED')";
        String sqlWithParams = "SELECT * FROM orders WHERE status IN (?, ?, ?, ?)";

        SqlQueryFingerprinter.FingerprintResult res1 = SqlQueryFingerprinter.fingerprint(sql1);
        SqlQueryFingerprinter.FingerprintResult res2 = SqlQueryFingerprinter.fingerprint(sql2);
        SqlQueryFingerprinter.FingerprintResult resParams = SqlQueryFingerprinter.fingerprint(sqlWithParams);

        assertThat(res1.normalizedSql()).isEqualTo("SELECT * FROM orders WHERE status IN (?)");
        assertThat(res2.normalizedSql()).isEqualTo("SELECT * FROM orders WHERE status IN (?)");
        assertThat(resParams.normalizedSql()).isEqualTo("SELECT * FROM orders WHERE status IN (?)");

        // Deterministic identical fingerprint across varying IN-list sizes
        assertThat(res1.fingerprint()).isEqualTo(res2.fingerprint());
        assertThat(res1.fingerprint()).isEqualTo(resParams.fingerprint());
    }

    @Test
    @DisplayName("Collapses multi-row VALUES clauses in batch inserts")
    void testCollapsesBatchInsertValues() {
        String sql = "INSERT INTO students (id, name, age) VALUES (1, 'John', 20), (2, 'Jane', 22), (3, 'Bob', 21)";
        SqlQueryFingerprinter.FingerprintResult result = SqlQueryFingerprinter.fingerprint(sql);

        assertThat(result.normalizedSql()).isEqualTo("INSERT INTO students (id, name, age) VALUES (?)");
        assertThat(result.operation()).isEqualTo("INSERT");
        assertThat(result.targetTable()).isEqualTo("students");
    }

    @Test
    @DisplayName("Detects UPSERT queries with ON CONFLICT")
    void testDetectsUpsert() {
        String sql = "INSERT INTO counters (name, val) VALUES ('hits', 1) ON CONFLICT (name) DO UPDATE SET val = counters.val + 1";
        SqlQueryFingerprinter.FingerprintResult result = SqlQueryFingerprinter.fingerprint(sql);

        assertThat(result.operation()).isEqualTo("UPSERT");
        assertThat(result.targetTable()).isEqualTo("counters");
    }

    @Test
    @DisplayName("Strips SQL comments and normalizes irregular whitespace")
    void testStripsCommentsAndNormalizesWhitespace() {
        String sql = """
                /* Block comment before query */
                SELECT   id,   email -- line comment
                FROM    "users"
                WHERE   active = 1
                """;
        SqlQueryFingerprinter.FingerprintResult result = SqlQueryFingerprinter.fingerprint(sql);

        assertThat(result.normalizedSql()).isEqualTo("SELECT id, email FROM \"users\" WHERE active = ?");
        assertThat(result.targetTable()).isEqualTo("users");
    }

    @Test
    @DisplayName("Fingerprints BoundSql instances")
    void testFingerprintBoundSql() {
        BoundSql boundSql = new BoundSql("UPDATE accounts SET balance = balance - ? WHERE id = ?", List.of(100, 5));
        SqlQueryFingerprinter.FingerprintResult result = SqlQueryFingerprinter.fingerprint(boundSql);

        assertThat(result.operation()).isEqualTo("UPDATE");
        assertThat(result.targetTable()).isEqualTo("accounts");
        assertThat(result.normalizedSql()).isEqualTo("UPDATE accounts SET balance = balance - ? WHERE id = ?");
    }

    @Test
    @DisplayName("Extracts operation and table from DELETE")
    void testDeleteFingerprint() {
        String sql = "DELETE FROM items WHERE expires_at < '2026-01-01'";
        SqlQueryFingerprinter.FingerprintResult result = SqlQueryFingerprinter.fingerprint(sql);

        assertThat(result.operation()).isEqualTo("DELETE");
        assertThat(result.targetTable()).isEqualTo("items");
    }

    @Test
    @DisplayName("Fingerprint is deterministic and SHA-256 compliant")
    void testDeterministicSha256() {
        String sql1 = "SELECT id FROM departments WHERE id = 10";
        String sql2 = "SELECT id FROM departments WHERE id = 9999";

        SqlQueryFingerprinter.FingerprintResult res1 = SqlQueryFingerprinter.fingerprint(sql1);
        SqlQueryFingerprinter.FingerprintResult res2 = SqlQueryFingerprinter.fingerprint(sql2);

        assertThat(res1.fingerprint()).isEqualTo(res2.fingerprint());
        assertThat(res1.fingerprint()).matches("^[0-9a-f]{64}$");
    }
}
